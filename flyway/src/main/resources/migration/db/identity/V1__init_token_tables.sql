-- ============================================
-- Identity Module: Token Storage Tables
-- PostgreSQL Compatible Version
-- ============================================

-- Token storage table
CREATE TABLE IF NOT EXISTS public.t_token
(
    uuid           VARCHAR(255) PRIMARY KEY       NOT NULL,
    created_date   TIMESTAMP(6) WITHOUT TIME ZONE,
    deleted        VARCHAR(255),
    is_disabled    BOOLEAN,
    is_out_of_sync BOOLEAN,
    locked         BOOLEAN,
    modified_date  TIMESTAMP(6) WITHOUT TIME ZONE,
    version_number BIGINT,
    deletion_time  BIGINT,

    -- Token fields
    token_value    VARCHAR(2048)                  NOT NULL,
    token_type     VARCHAR(50)                    NOT NULL,
    user_id        VARCHAR(255)                   NOT NULL,
    username       VARCHAR(255)                   NOT NULL,

    -- JWT specific fields
    jwt_id         VARCHAR(255),
    jwt_subject    VARCHAR(255),
    jwt_issuer     VARCHAR(255),
    jwt_audience   VARCHAR(255),

    -- Token lifecycle
    issued_at      TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    expires_at     TIMESTAMP(6) WITHOUT TIME ZONE,
    revoked_at     TIMESTAMP(6) WITHOUT TIME ZONE,

    -- Metadata
    metadata       TEXT
);

-- Constraints
ALTER TABLE public.t_token
    ADD CONSTRAINT uk_token_value UNIQUE (token_value);

ALTER TABLE public.t_token
    ADD CONSTRAINT chk_token_type CHECK (token_type IN ('JWT', 'UUID'));

-- Optional FK
-- ALTER TABLE public.t_token
--     ADD CONSTRAINT fk_token_user_id
--     FOREIGN KEY (user_id) REFERENCES public.t_user(uuid);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_token_user_id
    ON public.t_token (user_id);

CREATE INDEX IF NOT EXISTS idx_token_username
    ON public.t_token (username);

CREATE INDEX IF NOT EXISTS idx_token_type
    ON public.t_token (token_type);

CREATE INDEX IF NOT EXISTS idx_token_expires_at
    ON public.t_token (expires_at);

CREATE INDEX IF NOT EXISTS idx_token_revoked_at
    ON public.t_token (revoked_at);

CREATE INDEX IF NOT EXISTS idx_token_jwt_id
    ON public.t_token (jwt_id)
    WHERE jwt_id IS NOT NULL;



-- ============================================
-- Token Refresh Mapping Table
-- ============================================

CREATE TABLE IF NOT EXISTS public.t_token_refresh
(
    uuid             VARCHAR(255) PRIMARY KEY       NOT NULL,
    created_date     TIMESTAMP(6) WITHOUT TIME ZONE,
    deleted          VARCHAR(255),
    is_disabled      BOOLEAN,
    is_out_of_sync   BOOLEAN,
    locked           BOOLEAN,
    modified_date    TIMESTAMP(6) WITHOUT TIME ZONE,
    version_number   BIGINT,
    deletion_time    BIGINT,

    access_token_id  VARCHAR(255)                   NOT NULL,
    refresh_token_id VARCHAR(255)                   NOT NULL,

    expires_at       TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    revoked_at       TIMESTAMP(6) WITHOUT TIME ZONE
);

-- Constraints
ALTER TABLE public.t_token_refresh
    ADD CONSTRAINT uk_refresh_token UNIQUE (refresh_token_id);

ALTER TABLE public.t_token_refresh
    ADD CONSTRAINT fk_refresh_access_token
        FOREIGN KEY (access_token_id) REFERENCES public.t_token (uuid);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_refresh_access_token
    ON public.t_token_refresh (access_token_id);

CREATE INDEX IF NOT EXISTS idx_refresh_expires_at
    ON public.t_token_refresh (expires_at);
