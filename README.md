This repository was created to test Liquibase schema migrations with CRDB and verify changesets with transaction DDL / DML

To start we can create a single-node instance on our local machine
```
cockroach start-single-node \
  --certs-dir=./certs \
  --store=./data \
  --listen-addr=localhost:26257 \
  --background
```
![image](https://github.com/user-attachments/assets/73b9a104-5136-48c4-9db9-16d6f51dd212)

Then connect to the database and execute the following sql to setup the sample database
```
drop database if exists bookdb;
drop user if exists bookadmin;
drop user if exists bookuser;
create database if not exists bookdb;
use bookdb;
create schema if not exists library;
create user if not exists bookadmin with login password 'password';
grant all on database bookdb to bookadmin;
grant all on schema library to bookadmin;
create user if not exists bookuser with login password 'password';
grant usage on schema library to bookuser;
grant select, insert, update, delete on all tables in schema library to bookuser;
```
![image](https://github.com/user-attachments/assets/a557d76e-f905-4d91-9ae5-0e88df62aa16)

# Setup
Below are the to setup a local dev environment on MacOS

## Java
```
brew install openjdk@17
sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk
java --version
```

## Maven
```
brew install maven
mvn -v
```

## VS Code
```
brew install --cask visual-studio-code
```

## Git
```
brew install git
mkdir -p workspace && cd workspace
git clone https://github.com/roachlong/liquibase-demo.git
cd liquibase-demo
```

# Failed Transaction Rollback Scenario
Now open the liquibase-demo project folder in VS Code and run the LiquibaseDemoApplication launch configuration to test the scenario outlined in the changelog.

1) running the launch configuration will execute the liqubase changesets including the test-changeset-transaction
![image](https://github.com/user-attachments/assets/cf6341ad-927d-4bff-a901-1ecd70a6bd64)

2) the first insert into the event table from the changeset will succeed
3) the second insert statement fails because the events table (with an s) does not exist
4) the expectation is the entire changeset should rollback, since both statements should execute as a single transaction

5) but you can see that the first record committed and is left in the table after the changeset failure
![image](https://github.com/user-attachments/assets/7e33e28b-01f9-4d8a-961f-09e4f782ff7f)


# Change String Based Primary Key to UUID
For this scenario we want to comment out the include test-changeset-transaction.xml and change-message-primary-key.xml files in the changelog-master.xml and then run the LiquibaseDemoApplication launch configuration.

The first changelog, add-message.xml, creates a representative table with a partial index and populates the table with a million sample records.  Note that we explicitly include he id field in the partial index so that we can avoid issues from a low cardinality index key.  And we may have to recreate the index when we switch the primary key.

To test the primary key data type change we first want to start a db workload so we can verify the online schema change process while activity is running on the database.

We can create workloads to test a variety of scenarios, including implicit and explicit transactions, bulk writes, simulate contention, connection swarms, etc.  And we can control the velocity and volume of the workload with custom properties.  I've created a few examples for message table described below.
* num_connections: we'll simulate the workload across a number of processes
* duration: the number of minutes for which we want to run the simulation
* insert_freq: the percentage of cycles we want to insert new messages
* update_freq: the percentage of cycles we want to update existing messages
* delete_freq: the percentage of cycles we want to delete random messages
* read_freq: the percentage of cycles we read random messages
* batch_size: the number of records we want to touch in a single cycle
* delay: the number of milliseconds we should pause between transactions, so we don't overload admission controls

We'll store this information as variables in the terminal shell window. On Mac variables are assigned like ```my_var="example"``` and on Windows we proceed the variable assignment with a $ symbol ```$my_var="example"```.
```
conn_str="postgresql://root@localhost:26257/bookdb?sslmode=require&sslcert=certs%2Fclient.root.crt&sslkey=certs%2Fclient.root.key&sslrootcert=certs%2Fca.crt"

num_connections=4
duration=60
insert_freq=50
update_freq=50
delete_freq=50
read_freq=50
batch_size=1
delay=100
```

Then we can use our dbworkload script to simulate the workload long enough to test our online schema changes.  **Note**: with Windows PowerShell replace each backslash double quote(\\") with a pair of double quotes around the json properties, i.e. ``` ""batch_size"": ""$batch_size"" ```
```
dbworkload run -w messages.py -c $num_connections -d $(( ${duration} * 60 )) --uri "$conn_str" --args "{
        \"insert_freq\": $insert_freq,
        \"update_freq\": $update_freq,
        \"delete_freq\": $delete_freq,
        \"read_freq\": $read_freq,
        \"batch_size\": $batch_size,
        \"delay\": $delay
    }"
```

The second changelog, change-message-primary-key.xml, is set up as a series of changesets to optimize switching the data type of a string based UUID primary key column to a UUID field.  But before we uncomment that change log from the master we'll test the series of queries manually from the sql console first.

**HOWEVER**: If you're running the dbworkload script it will create contention and we can't handle retry logic inside the stored proc.  Therefore you'll need to uncomment the liquibase change log for the primary key update and let the tools execute the schema chnages below.


1) Let's add the new UUID column to the table first and wait for the schema change to complete.
```
cockroach sql --url $conn_str -e """
ALTER TABLE bookdb.library.message ADD COLUMN id_uuid UUID;
CREATE INDEX idx_message_tbc ON library.message (id) STORING (id_uuid) WHERE id_uuid IS NULL;
"""
```
Then wait for the jobs to complete, which you can verify from the CRDB admin console, i.e. https://localhost:8080/#/jobs and look for the statement above to show succeeded.  Or execute the query below and look for a succeeded status.
```
cockroach sql --url $conn_str -e """
SELECT status FROM [SHOW JOBS] WHERE description LIKE 'ALTER TABLE bookdb.library.message%' ORDER BY created DESC LIMIT 1;
SELECT status FROM [SHOW JOBS] WHERE description LIKE 'CREATE INDEX idx_message_tbc%' ORDER BY created DESC LIMIT 1;
"""
```


2) Then let's create a stored procedure to backfil the UUID values for our new column
```
cockroach sql --url $conn_str

CREATE OR REPLACE PROCEDURE library.backfill_id_uuid()
LANGUAGE plpgsql
AS $$
DECLARE
    remaining_count INT;
BEGIN
  LOOP
    SELECT count(1) INTO remaining_count
    FROM library.message
    WHERE id_uuid IS NULL;

    IF remaining_count = 0 THEN
      RAISE NOTICE 'Backfill complete for library.message.';
      EXIT;
    END IF;

    RAISE NOTICE '% remaining records, continuing...', remaining_count;

    UPDATE library.message
    SET id_uuid = id::UUID
    WHERE id_uuid IS NULL
    AND id IN (
      SELECT id
      FROM library.message
      WHERE id_uuid IS NULL
      LIMIT 10000
    );

    COMMIT;
  END LOOP;
END;
$$;
```

And call the procedure to populate the data for our table.
```
cockroach sql --url $conn_str -e """
call library.backfill_id_uuid();
"""
```


3) Now we can set the NOT NULL and DEFAULT properties for the new id_uuid column.
```
cockroach sql --url $conn_str -e """
ALTER TABLE library.message
    ALTER COLUMN id_uuid SET NOT NULL,
    ALTER COLUMN id_uuid SET DEFAULT gen_random_uuid();
"""
```
Then wait for the jobs to complete, which you can verify from the CRDB admin console, i.e. https://localhost:8080/#/jobs and look for the statement above to show succeeded.  Or execute the query below and look for a succeeded status.
```
cockroach sql --url $conn_str -e """
SELECT status FROM [SHOW JOBS] WHERE description LIKE 'ALTER TABLE bookdb.library.message%' ORDER BY created DESC LIMIT 1;
"""
```


4) Next we can switch the primary key to the new id_uuid column.
```
cockroach sql --url $conn_str -e """
ALTER TABLE library.message ALTER PRIMARY KEY USING COLUMNS (id_uuid);
"""
```
Then wait for the jobs to complete, which you can verify from the CRDB admin console, i.e. https://localhost:8080/#/jobs and look for the statement above to show succeeded.  Or execute the query below and look for a succeeded status.
```
cockroach sql --url $conn_str -e """
SELECT status FROM [SHOW JOBS] WHERE description LIKE 'ALTER TABLE bookdb.library.message%' ORDER BY created DESC LIMIT 1;
"""
```


5) And switch the names for the current id and the new id_uuid columns.
```
cockroach sql --url $conn_str -e """
DROP PROCEDURE library.backfill_id_uuid();
ALTER TABLE library.message RENAME COLUMN id TO id_string;
ALTER TABLE library.message RENAME COLUMN id_uuid TO id;
"""
```
Then wait for the jobs to complete, which you can verify from the CRDB admin console, i.e. https://localhost:8080/#/jobs and look for the statement above to show succeeded.  Or execute the query below and look for a succeeded status.
```
cockroach sql --url $conn_str -e """
SELECT status FROM [SHOW JOBS] WHERE description LIKE 'ALTER TABLE bookdb.library.message%' ORDER BY created DESC LIMIT 1;
"""
```


6) Finally we can drop the old id_string column.
```
cockroach sql --url $conn_str -e """
ALTER TABLE library.message DROP COLUMN id_string;
"""
```
Then wait for the jobs to complete, which you can verify from the CRDB admin console, i.e. https://localhost:8080/#/jobs and look for the statement above to show succeeded.  Or execute the query below and look for a succeeded status.
```
cockroach sql --url $conn_str -e """
SELECT status FROM [SHOW JOBS] WHERE description LIKE 'ALTER TABLE bookdb.library.message%' ORDER BY created DESC LIMIT 1;
"""
```
