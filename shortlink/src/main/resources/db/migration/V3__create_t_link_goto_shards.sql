CREATE TABLE t_link_goto
(
    id             BIGINT NOT NULL DEFAULT nextval('t_link_goto_id_seq'),
    gid            VARCHAR(32)     DEFAULT 'default',
    full_short_url VARCHAR(128)    DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE (full_short_url)
);