# Opens a PostgreSQL connection per API request using the DATABASE_URL environment variable.
import os
from collections.abc import Iterator

import psycopg
from psycopg.rows import dict_row


def get_connection() -> Iterator[psycopg.Connection]:
    # FastAPI dependency: one connection per request. Autocommit is on, so conn.transaction()
    # blocks are real transactions rather than nested savepoints.
    url = os.environ.get("DATABASE_URL")
    if not url:
        raise RuntimeError("DATABASE_URL is not set (see python-forecast/README.md)")

    with psycopg.connect(url, autocommit=True, row_factory=dict_row) as conn:
        yield conn
