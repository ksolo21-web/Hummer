#!/usr/bin/env python3
from __future__ import annotations
from pathlib import Path
import base64, gzip, hashlib

ROOT = Path(__file__).resolve().parents[1]
FILES = {
