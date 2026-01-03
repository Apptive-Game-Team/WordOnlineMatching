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

ALTER TABLE cards
    ADD COLUMN IF NOT EXISTS unlock_condition_type VARCHAR(31),
    ADD COLUMN IF NOT EXISTS unlock_required_value INT;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS total_wins INT NOT NULL DEFAULT 0;

UPDATE cards
SET unlock_condition_type = 'WIN_COUNT',
    unlock_required_value = 5
WHERE name = 'Wind';

UPDATE cards
SET unlock_condition_type = 'WIN_COUNT',
    unlock_required_value = 10
WHERE name = 'Drop';


DROP TABLE quests;

CREATE TABLE quests (
    id BIGSERIAL PRIMARY KEY,
    progress_checker VARCHAR(31), -- spring bean name (return int progress_value)
    require_value INT NOT NULL,
    reward_giver VARCHAR(31) -- spring bean name
);

DROP TABLE reward_params;

CREATE TABLE reward_params (
    id BIGSERIAL PRIMARY KEY,
    quest_id BIGINT REFERENCES quests(id) ON DELETE CASCADE,
    name VARCHAR(31) NOT NULL,
    value INT NOT NULL
);

ALTER TABLE reward_params ADD CONSTRAINT uq_reward_params_quest_id_name UNIQUE (quest_id, name);


CREATE TABLE user_quests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    quest_id BIGINT REFERENCES quests(id) ON DELETE CASCADE,
    state VARCHAR(15) NOT NULL DEFAULT 'IN_PROGRESS' -- 'PENDING', 'IN_PROGRESS', 'COMPLETED'
);
