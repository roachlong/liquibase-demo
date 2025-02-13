drop table if exists library.book;

create table library.book (
    id uuid not null,
    isbn varchar(255),
    publisher varchar(255),
    title varchar(255),
    primary key (id)
);