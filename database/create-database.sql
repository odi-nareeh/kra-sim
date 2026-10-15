-- One-time setup: creates an empty database. Flyway builds everything inside it.
DROP DATABASE IF EXISTS kra_sim_db;
CREATE DATABASE kra_sim_db TEMPLATE template0;