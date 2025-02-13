This repository was created to test Liquibase schema migrations with CRDB and verify changesets with transaction DDL / DML

To start we can create a single-node instance on our local machine
```
cockroach start-single-node --certs-dir=./certs --store=./data
```

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

Next open the liquibase-demo project folder in VS Code and run the LiquibaseDemoApplication launch configuration to test a scenario.
