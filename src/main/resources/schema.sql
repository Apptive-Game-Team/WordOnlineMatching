CREATE TABLE decorations (
    id BIGSERIAL PRIMARY KEY,
    deco_type VARCHAR(10) NOT NULL , -- Hat, Cape
    name VARCHAR(20) NOT NULL
);

CREATE TABLE user_decorations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    decoration_id BIGINT REFERENCES decorations(id)
);

ALTER TABLE user_decorations ADD COLUMN is_equipped BOOLEAN DEFAULT FALSE;
