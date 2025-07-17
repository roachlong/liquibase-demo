This repository was created to test Liquibase schema migrations with CRDB and verify changesets with transaction DDL / DML

To start we can create a single-node instance on our local machine
```
cockroach start-single-node --certs-dir=./certs --store=./data
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
For this scenario we want to comment out the include test-changeset-transaction.xml file in the changelog-master.xml and then run the LiquibaseDemoApplication launch configuration.

The first changelog, add-message.xml, creates a representative table with a partial index and populates the table with a million sample records.  Note that the partial index doesn't include the id field, since the primary key is implicitly stored with all secondary indexes.  And the side effect is that we don't need to redefine the secondary index when we switch the primary key.

The second changelog, change-message-primary-key.xml, is set up as a series of changesets to optimize switching the data type of a string based UUID primary key column to a UUID field.

1) First we add a new nullable UUID column to the table
2) Then we'll backfill the column with the current id field string values cast to a UUID
3) Now we can alter the UUID column with a default value and NOT NULL property
4) And replace the primary key with the new UUID column
5) Then we switch the names of our string based and UUID columns
6) And finally we can drop the old string based column

The backfill step will take the longest time because it is a large transaction.  If necessary, we can improve performance by creating a Java component that will batch the updates into smaller transactions.  However, all of the other changesets are nearly instantaneous with 1MM records and low activity on the database.





