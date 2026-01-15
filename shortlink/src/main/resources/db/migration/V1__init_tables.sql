create table public.t_link
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
    clicknum       integer,
    createdtype    integer,
    deltime        bigint,
    description    character varying(255),
    domain         character varying(255),
    enablestatus   integer,
    favicon        character varying(255),
    fullshorturl   character varying(255),
    gid            character varying(255),
    originurl      character varying(255),
    shorturi       character varying(255),
    validdate      timestamp(6) without time zone,
    validdatetype  integer
);

create table public.t_link_goto
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
    fullshorturl   character varying(255),
    gid            character varying(255)
);