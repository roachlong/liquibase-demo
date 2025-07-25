import psycopg
import random
import time

class Messages:

    def __init__(self, args: dict):
        # args is a dict of string passed with the --args flag
        # user passed a yaml/json, in python that's a dict object
        self.insert_freq: int = int(args.get("insert_freq", 25))
        self.update_freq: int = int(args.get("update_freq", 25))
        self.delete_freq: int = int(args.get("delete_freq", 25))
        self.read_freq: int = int(args.get("read_freq", 25))
        self.batch_size: int = int(args.get("batch_size", 1))
        self.delay: int = int(args.get("delay", 1000))

        # you can arbitrarely add any variables you want
        self.counter: int = 0



    # the setup() function is executed only once
    # when a new executing thread is started.
    # Also, the function is a vector to receive the excuting threads's unique id and the total thread count
    def setup(self, conn: psycopg.Connection, id: int, total_thread_count: int):
        self.id = id
        with conn.cursor() as cur:
            print(
                f"My thread ID is {id}. The total count of threads is {total_thread_count}"
            )
            print(cur.execute(f"select version()").fetchone()[0])



    # the run() function returns a list of functions
    # that dbworkload will execute, sequentially.
    # Once every func has been executed, run() is re-evaluated.
    # This process continues until dbworkload exits.
    def loop(self):   
        return [self.insert, self.update, self.delete, self.read]



    # conn is an instance of a psycopg connection object
    # conn is set by default with autocommit=True, so no need to send a commit message
    def messages(self, conn: psycopg.Connection):
        try:
            query = f"""
                SELECT id
                FROM library.message
                AS OF SYSTEM TIME follower_read_timestamp()
                WHERE id >= gen_random_uuid()::STRING
                ORDER BY id
                LIMIT {self.batch_size};
            """
            with conn.cursor() as cur:
                cur.execute(query)
                return [row[0] for row in cur]
        
        except (psycopg.errors.InvalidParameterValue, psycopg.errors.DatatypeMismatch, psycopg.errors.InvalidTextRepresentation):
            # Likely due to type mismatch (e.g., id is now UUID)
            fallback_query = f"""
                SELECT id
                FROM library.message
                AS OF SYSTEM TIME follower_read_timestamp()
                WHERE id >= gen_random_uuid()
                ORDER BY id
                LIMIT {self.batch_size};
            """
            with conn.cursor() as cur:
                cur.execute(fallback_query)
                return [row[0] for row in cur]



    # conn is an instance of a psycopg connection object
    # conn is set by default with autocommit=True, so no need to send a commit message
    def insert(self, conn: psycopg.Connection):
        if random.randint(1, 100) > self.insert_freq:
            return

        try:
            query = f"""
                INSERT INTO library.message (id, text, read)
                SELECT
                    gen_random_uuid()::STRING AS id,
                    'Message #' || g || ': ' || md5(random()::STRING) AS text,
                    CASE
                        WHEN g % 100 = 0 THEN false
                        ELSE true
                    END AS read
                FROM generate_series(1, {self.batch_size}) AS g;
            """
            with conn.cursor() as cur:
                cur.execute(query)
            time.sleep(self.delay / 1000)
        
        except (psycopg.errors.DatatypeMismatch, psycopg.errors.InvalidTextRepresentation):
            # Likely due to type mismatch (e.g., id is now UUID)
            fallback_query = f"""
                INSERT INTO library.message (text, read)
                SELECT
                    'Message #' || g || ': ' || md5(random()::STRING) AS text,
                    CASE
                        WHEN g % 100 = 0 THEN false
                        ELSE true
                    END AS read
                FROM generate_series(1, {self.batch_size}) AS g;
            """
            with conn.cursor() as cur:
                cur.execute(fallback_query)

        time.sleep(self.delay / 1000)



    # conn is an instance of a psycopg connection object
    # conn is set by default with autocommit=True, so no need to send a commit message
    def update(self, conn: psycopg.Connection):
        if random.randint(1, 100) > self.update_freq:
            return

        message_ids = self.messages(conn)
        if not message_ids:
            return
        
        placeholders = ','.join(f"%s" for i in range(len(message_ids)))
        query = f"""
            UPDATE library.message
            SET text = 'Updated Message: ' || md5(random()::STRING)
            WHERE id IN ({placeholders});
        """

        with conn.cursor() as cur:
            cur.execute(query, tuple(message_ids))

        time.sleep(self.delay / 1000)



    # conn is an instance of a psycopg connection object
    # conn is set by default with autocommit=True, so no need to send a commit message
    def delete(self, conn: psycopg.Connection):
        if random.randint(1, 100) > self.delete_freq:
            return

        message_ids = self.messages(conn)
        if not message_ids:
            return
        
        placeholders = ','.join(f"%s" for i in range(len(message_ids)))
        query = f"""
            DELETE FROM library.message
            WHERE id IN ({placeholders});
        """

        with conn.cursor() as cur:
            cur.execute(query, tuple(message_ids))

        time.sleep(self.delay / 1000)



    # conn is an instance of a psycopg connection object
    # conn is set by default with autocommit=True, so no need to send a commit message
    def read(self, conn: psycopg.Connection):
        if random.randint(1, 100) > self.read_freq:
            return

        message_ids = self.messages(conn)
        if not message_ids:
            return
        
        placeholders = ','.join(f"%s" for i in range(len(message_ids)))
        query = f"""
            SELECT id, text, read
            FROM library.message
            WHERE id IN ({placeholders});
        """

        with conn.cursor() as cur:
            cur.execute(query, tuple(message_ids))
            rows = cur.fetchall()

        time.sleep(self.delay / 1000)
