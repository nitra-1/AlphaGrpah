CREATE SCHEMA IF NOT EXISTS api;

CREATE FUNCTION api.set_updated_at()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

CREATE TABLE api.platform_users (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email          text NOT NULL UNIQUE,
    password_hash  text NOT NULL,
    role           text NOT NULL CHECK (role IN ('ADMIN', 'SYSTEM')),
    active         boolean NOT NULL DEFAULT true,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_platform_users_updated_at
    BEFORE UPDATE ON api.platform_users
    FOR EACH ROW EXECUTE FUNCTION api.set_updated_at();
