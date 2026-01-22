create table public.t_user
(
    uuid           character varying(255) primary key not null,
    created_date   timestamp(6) without time zone,
    deleted        character varying(255),
    is_disabled    boolean,
    is_out_of_sync boolean,
    locked         boolean,
    modified_date  timestamp(6) without time zone,
    version_number bigint,
    deletiontime   bigint,
    mail           character varying(255),
    password       character varying(255),
    phone          character varying(255),
    realname       character varying(255),
    username       character varying(255),
    deletion_time  bigint,
    real_name      character varying(255)
);

create table public.t_group
(
    uuid           character varying(255) primary key not null,
    created_date   timestamp(6) without time zone,
    deleted        character varying(255),
    is_disabled    boolean,
    is_out_of_sync boolean,
    locked         boolean,
    modified_date  timestamp(6) without time zone,
    version_number bigint,
    display_name   character varying(1024),
    name           character varying(1024),
    gid            character varying(255),
    sortorder      integer,
    username       character varying(255),
    sort_order     integer
);

create table public.t_group_unique
(
    uuid           character varying(255) primary key not null,
    created_date   timestamp(6) without time zone,
    deleted        character varying(255),
    is_disabled    boolean,
    is_out_of_sync boolean,
    locked         boolean,
    modified_date  timestamp(6) without time zone,
    version_number bigint,
    gid            character varying(255)
);



