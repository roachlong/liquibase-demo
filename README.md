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

# Execution
Now open the liquibase-demo project folder in VS Code and run the LiquibaseDemoApplication launch configuration to test the scenario outlined in the changelog.

1) running the launch configuration will execute the liqubase changesets including the test-changeset-transaction
![image](https://github.com/user-attachments/assets/cf6341ad-927d-4bff-a901-1ecd70a6bd64)

2) the first insert into the event table from the changeset will succeed
3) the second insert statement fails because the events table (with an s) does not exist
4) the expectation is the entire changeset should rollback, since both statements should execute as a single transaction

5) but you can see that the first record committed and is left in the table after the changeset failure
![image](https://github.com/user-attachments/assets/7e33e28b-01f9-4d8a-961f-09e4f782ff7f)








