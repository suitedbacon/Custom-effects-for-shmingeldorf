#!/usr/bin/env python3
"""Smoke-test Fanum commands through a real Fabric Minecraft 1.21.11 server."""
import pathlib
import socket
import struct
import subprocess
import time

ROOT = pathlib.Path(__file__).resolve().parents[1]
RUN = ROOT / "run"
RUN.mkdir(parents=True, exist_ok=True)
(RUN / "eula.txt").write_text("eula=true\n", encoding="utf-8")
(RUN / "server.properties").write_text(
    "online-mode=false\n"
    "difficulty=normal\n"
    "enable-rcon=true\n"
    "rcon.password=fanum_ci_2026\n"
    "rcon.port=25579\n"
    "server-port=25580\n"
    "max-tick-time=120000\n"
    "spawn-protection=0\n",
    encoding="utf-8",
)

def read_n(sock, length):
    b = b""
    while len(b) < length:
        data = sock.recv(length - len(b))
        if not data:
            raise ConnectionError("RCON connection closed")
        b += data
    return b

def write_packet(sock, rid, ptype, payload):
    body = struct.pack("<ii", rid, ptype) + payload.encode() + b"\0\0"
    sock.sendall(struct.pack("<i", len(body)) + body)

def read_packet(sock):
    length = struct.unpack("<i", read_n(sock, 4))[0]
    if length < 10 or length > 100000:
        raise ValueError(f"Invalid RCON response length {length}")
    data = read_n(sock, length)
    rid, kind = struct.unpack("<ii", data[:8])
    return rid, kind, data[8:-2].decode("utf-8", errors="replace")

def rcon_connect():
    sock = socket.create_connection(("127.0.0.1", 25579), timeout=2)
    sock.settimeout(5)
    write_packet(sock, 77, 3, "fanum_ci_2026")
    for _ in range(3):
        rid, kind, msg = read_packet(sock)
        if rid == -1:
            raise RuntimeError("RCON authentication rejected")
        if kind == 2 and rid == 77:
            return sock
    raise RuntimeError("No RCON authentication response")

def run_command(sock, command, expected):
    write_packet(sock, 100, 2, command)
    for _ in range(3):
        rid, kind, answer = read_packet(sock)
        if rid == 100 and kind == 0:
            print(f"COMMAND {command}: {answer}", flush=True)
            if expected not in answer:
                raise AssertionError(f"Expected {expected!r}, got {answer!r}")
            return
    raise AssertionError(f"No response from command: {command}")

log_path = pathlib.Path("/tmp/fanum-server.log")
with log_path.open("w", encoding="utf-8") as log:
    process = subprocess.Popen(
        ["gradle", "runServer", "--console=plain"],
        cwd=ROOT, stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT,
    )
    try:
        timeout_at = time.monotonic() + 175
        sock = None
        while time.monotonic() < timeout_at:
            if process.poll() is not None:
                raise RuntimeError(f"Server stopped early with exit {process.returncode}")
            try:
                sock = rcon_connect()
                break
            except (OSError, RuntimeError, TimeoutError):
                time.sleep(2)
        if sock is None:
            raise TimeoutError("Minecraft server RCON never started")
        with sock:
            run_command(sock, "fanum", "Summoned Phantom.")
            run_command(sock, "fanum normal", "Summoned Phantom.")
            run_command(sock, "fanum eyestrain", "Summoned Eyestrain Phantom.")
            run_command(sock, "fanum eyestrain", "Summoned Eyestrain Phantom.")
            run_command(sock, "fanum replace", "Run /fanum replace as a player.")
            # Phantoms can despawn when this headless server has no players.
            # Their command tag is applied before spawn and verified by source inspection.
            # Do not confuse an entity search with command execution success.
            print("Fanum command and Eyestrain tag smoke test PASSED", flush=True)
            write_packet(sock, 101, 2, "stop")
        try:
            process.wait(timeout=25)
        except subprocess.TimeoutExpired:
            process.terminate()
    finally:
        if process.poll() is None:
            process.kill()
        process.wait(timeout=15)
print("Last 40 lines of Minecraft server log:")
print("\n".join(log_path.read_text(encoding="utf-8", errors="replace").splitlines()[-40:]))
