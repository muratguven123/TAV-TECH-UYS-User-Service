DROP TRIGGER IF EXISTS trg_users_update_timestamp ON users;
DROP FUNCTION IF EXISTS fn_update_users_timestamp();
DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS roles;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(60) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_users_username ON users USING BTREE (username);

CREATE OR REPLACE FUNCTION fn_update_users_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_update_timestamp
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION fn_update_users_timestamp();

CREATE TABLE roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(20) NOT NULL UNIQUE,
    CONSTRAINT ck_roles_name CHECK (name IN ('ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_ANALYST'))
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id INT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);

INSERT INTO roles (name)
VALUES ('ROLE_ADMIN'), ('ROLE_OPERATOR'), ('ROLE_ANALYST')
ON CONFLICT (name) DO NOTHING;
