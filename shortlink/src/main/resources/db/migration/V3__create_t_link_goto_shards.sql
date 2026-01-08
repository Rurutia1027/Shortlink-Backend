-- ================================================
-- Template table for shard 0
-- ================================================
CREATE TABLE t_link_goto_0
(
    id             BIGINT NOT NULL DEFAULT nextval('t_link_goto_id_seq'),
    gid            VARCHAR(32)     DEFAULT 'default',
    full_short_url VARCHAR(128)    DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE (full_short_url)
);


-- ================================================
-- Shard 1
-- ================================================
CREATE TABLE t_link_goto_1
(
    LIKE t_link_goto_0 INCLUDING ALL
);

-- ================================================
-- Shard 2
-- ================================================
CREATE TABLE t_link_goto_2
(
    LIKE t_link_goto_0 INCLUDING ALL
);

-- ================================================
-- Shard 3
-- ================================================
CREATE TABLE t_link_goto_3
(
    LIKE t_link_goto_0 INCLUDING ALL
);
