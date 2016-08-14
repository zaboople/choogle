create schema myschema;

create table myschema.hits (
  create_date timestamp,
  resource_name varchar(512),
  resource_type varchar(64),
  hits integer
);
