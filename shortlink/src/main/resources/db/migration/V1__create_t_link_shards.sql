-- Flyway Migration: Create sharded t_link tables (4 shards)
-- Comments converted to English
-- Create the table using this sequence for the primary key
CREATE TABLE t_link_0
(
    id              BIGINT PRIMARY KEY DEFAULT nextval('t_link_id_seq'),
    domain          VARCHAR(128),
    short_uri       VARCHAR(8),
    full_short_url  VARCHAR(128),
    origin_url      TEXT,
    click_num       INT                DEFAULT 0,
    gid             VARCHAR(32)        DEFAULT 'default',
    favicon         VARCHAR(256),
    enable_status   SMALLINT,
    created_type    SMALLINT,
    valid_date_type SMALLINT,
    valid_date      TIMESTAMP,
    describe        VARCHAR(1024),
    total_pv        INT,
    total_uv        INT,
    total_uip       INT,
    create_time     TIMESTAMP          DEFAULT now(),
    update_time     TIMESTAMP          DEFAULT now(),
    del_time        BIGINT             DEFAULT 0,
    del_flag        SMALLINT           DEFAULT 0,
    CONSTRAINT uniq_full_short_url UNIQUE (full_short_url, del_time)
);


-- Shard 1
CREATE TABLE t_link_1
(
    LIKE t_link_0 INCLUDING ALL
);

-- Shard 2
CREATE TABLE t_link_2
(
    LIKE t_link_0 INCLUDING ALL
);

-- Shard 3
CREATE TABLE t_link_3
(
    LIKE t_link_0 INCLUDING ALL
);
