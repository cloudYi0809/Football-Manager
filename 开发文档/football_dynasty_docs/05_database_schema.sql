-- 《绿茵王朝：2002》SQLite 数据库建表参考
-- 说明：该 SQL 用于开发参考，实际 Room Entity 可按需拆分。

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS player (
    player_id INTEGER PRIMARY KEY,
    source_id TEXT,
    real_name TEXT NOT NULL,
    display_name TEXT,
    birth_date TEXT,
    nationality TEXT,
    second_nationality TEXT,
    height INTEGER,
    weight INTEGER,
    preferred_foot TEXT,
    primary_position TEXT,
    secondary_positions TEXT,
    personality TEXT,
    retire_age_base INTEGER DEFAULT 35,
    portrait_path TEXT,
    created_at TEXT,
    updated_at TEXT
);

CREATE TABLE IF NOT EXISTS player_attributes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_id INTEGER NOT NULL,
    season_id INTEGER NOT NULL,
    ca INTEGER DEFAULT 50,
    pa INTEGER DEFAULT 50,
    shooting INTEGER DEFAULT 50,
    finishing INTEGER DEFAULT 50,
    long_shots INTEGER DEFAULT 50,
    passing INTEGER DEFAULT 50,
    crossing INTEGER DEFAULT 50,
    dribbling INTEGER DEFAULT 50,
    technique INTEGER DEFAULT 50,
    first_touch INTEGER DEFAULT 50,
    pace INTEGER DEFAULT 50,
    acceleration INTEGER DEFAULT 50,
    strength INTEGER DEFAULT 50,
    stamina INTEGER DEFAULT 50,
    balance INTEGER DEFAULT 50,
    agility INTEGER DEFAULT 50,
    jumping INTEGER DEFAULT 50,
    defending INTEGER DEFAULT 50,
    tackling INTEGER DEFAULT 50,
    marking INTEGER DEFAULT 50,
    positioning INTEGER DEFAULT 50,
    heading INTEGER DEFAULT 50,
    vision INTEGER DEFAULT 50,
    decision INTEGER DEFAULT 50,
    composure INTEGER DEFAULT 50,
    leadership INTEGER DEFAULT 50,
    work_rate INTEGER DEFAULT 50,
    teamwork INTEGER DEFAULT 50,
    injury_proneness INTEGER DEFAULT 50,
    big_match INTEGER DEFAULT 50,
    consistency INTEGER DEFAULT 50,
    professionalism INTEGER DEFAULT 50,
    ambition INTEGER DEFAULT 50,
    loyalty INTEGER DEFAULT 50,
    gk_diving INTEGER DEFAULT 0,
    gk_reflexes INTEGER DEFAULT 0,
    gk_handling INTEGER DEFAULT 0,
    gk_positioning INTEGER DEFAULT 0,
    gk_one_on_one INTEGER DEFAULT 0,
    FOREIGN KEY(player_id) REFERENCES player(player_id)
);

CREATE TABLE IF NOT EXISTS club (
    club_id INTEGER PRIMARY KEY,
    source_id TEXT,
    club_name TEXT NOT NULL,
    country TEXT,
    city TEXT,
    founded_year INTEGER,
    reputation INTEGER DEFAULT 50,
    stadium_name TEXT,
    stadium_capacity INTEGER,
    training_level INTEGER DEFAULT 50,
    youth_level INTEGER DEFAULT 50,
    finance_level INTEGER DEFAULT 50,
    logo_path TEXT,
    kit_path TEXT,
    created_at TEXT,
    updated_at TEXT
);

CREATE TABLE IF NOT EXISTS season (
    season_id INTEGER PRIMARY KEY,
    year_start INTEGER NOT NULL,
    year_end INTEGER NOT NULL,
    label TEXT NOT NULL,
    start_date TEXT,
    end_date TEXT,
    is_historical INTEGER DEFAULT 1
);

CREATE TABLE IF NOT EXISTS competition (
    competition_id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    country TEXT,
    type TEXT,
    reputation INTEGER DEFAULT 50,
    level INTEGER DEFAULT 1,
    rules_json TEXT
);

CREATE TABLE IF NOT EXISTS club_competition_season (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    season_id INTEGER NOT NULL,
    competition_id INTEGER NOT NULL,
    club_id INTEGER NOT NULL,
    FOREIGN KEY(season_id) REFERENCES season(season_id),
    FOREIGN KEY(competition_id) REFERENCES competition(competition_id),
    FOREIGN KEY(club_id) REFERENCES club(club_id)
);

CREATE TABLE IF NOT EXISTS squad_membership (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    season_id INTEGER NOT NULL,
    club_id INTEGER NOT NULL,
    player_id INTEGER NOT NULL,
    squad_number INTEGER,
    joined_date TEXT,
    contract_until TEXT,
    wage INTEGER DEFAULT 0,
    market_value INTEGER DEFAULT 0,
    is_loan INTEGER DEFAULT 0,
    loan_from_club_id INTEGER,
    squad_role TEXT,
    FOREIGN KEY(season_id) REFERENCES season(season_id),
    FOREIGN KEY(club_id) REFERENCES club(club_id),
    FOREIGN KEY(player_id) REFERENCES player(player_id)
);

CREATE TABLE IF NOT EXISTS transfer_history (
    transfer_id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_id INTEGER NOT NULL,
    from_club_id INTEGER,
    to_club_id INTEGER,
    transfer_date TEXT NOT NULL,
    fee INTEGER DEFAULT 0,
    transfer_type TEXT,
    season_id INTEGER,
    is_historical INTEGER DEFAULT 1,
    was_interrupted INTEGER DEFAULT 0,
    notes TEXT,
    FOREIGN KEY(player_id) REFERENCES player(player_id)
);

CREATE TABLE IF NOT EXISTS match (
    match_id INTEGER PRIMARY KEY AUTOINCREMENT,
    season_id INTEGER NOT NULL,
    competition_id INTEGER NOT NULL,
    match_date TEXT NOT NULL,
    home_club_id INTEGER NOT NULL,
    away_club_id INTEGER NOT NULL,
    home_score_real INTEGER,
    away_score_real INTEGER,
    home_score_sim INTEGER,
    away_score_sim INTEGER,
    status TEXT DEFAULT 'scheduled',
    is_historical INTEGER DEFAULT 1,
    match_stats_json TEXT,
    FOREIGN KEY(season_id) REFERENCES season(season_id),
    FOREIGN KEY(competition_id) REFERENCES competition(competition_id),
    FOREIGN KEY(home_club_id) REFERENCES club(club_id),
    FOREIGN KEY(away_club_id) REFERENCES club(club_id)
);

CREATE TABLE IF NOT EXISTS historical_prospect_pool (
    prospect_id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_id INTEGER NOT NULL,
    discoverable_from TEXT NOT NULL,
    default_youth_club_id INTEGER,
    default_first_team_club_id INTEGER,
    default_breakthrough_year INTEGER,
    default_transfer_path TEXT,
    initial_region_code TEXT,
    hidden_until_discovered INTEGER DEFAULT 1,
    legend_level INTEGER DEFAULT 0,
    created_scenario TEXT,
    tags TEXT,
    FOREIGN KEY(player_id) REFERENCES player(player_id)
);

CREATE TABLE IF NOT EXISTS scout (
    scout_id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    nationality TEXT,
    age INTEGER,
    current_club_id INTEGER,
    judging_current_ability INTEGER DEFAULT 50,
    judging_potential INTEGER DEFAULT 50,
    adaptability INTEGER DEFAULT 50,
    negotiation INTEGER DEFAULT 50,
    network_level INTEGER DEFAULT 50,
    reputation INTEGER DEFAULT 50,
    salary INTEGER DEFAULT 0,
    FOREIGN KEY(current_club_id) REFERENCES club(club_id)
);

CREATE TABLE IF NOT EXISTS scout_region_knowledge (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scout_id INTEGER NOT NULL,
    region_code TEXT NOT NULL,
    knowledge_level INTEGER DEFAULT 50,
    FOREIGN KEY(scout_id) REFERENCES scout(scout_id)
);

CREATE TABLE IF NOT EXISTS scout_assignment (
    assignment_id INTEGER PRIMARY KEY AUTOINCREMENT,
    save_id INTEGER NOT NULL,
    scout_id INTEGER NOT NULL,
    region_code TEXT,
    task_type TEXT,
    target_position TEXT,
    min_age INTEGER,
    max_age INTEGER,
    budget_level TEXT,
    start_date TEXT,
    end_date TEXT,
    status TEXT DEFAULT 'active',
    progress INTEGER DEFAULT 0,
    FOREIGN KEY(scout_id) REFERENCES scout(scout_id)
);

CREATE TABLE IF NOT EXISTS scout_report (
    report_id INTEGER PRIMARY KEY AUTOINCREMENT,
    save_id INTEGER NOT NULL,
    scout_id INTEGER NOT NULL,
    player_id INTEGER NOT NULL,
    assignment_id INTEGER,
    report_date TEXT,
    knowledge_level INTEGER DEFAULT 1,
    estimated_ca_min INTEGER,
    estimated_ca_max INTEGER,
    estimated_pa_min INTEGER,
    estimated_pa_max INTEGER,
    strengths TEXT,
    weaknesses TEXT,
    risk_notes TEXT,
    recommendation_level TEXT,
    FOREIGN KEY(scout_id) REFERENCES scout(scout_id),
    FOREIGN KEY(player_id) REFERENCES player(player_id),
    FOREIGN KEY(assignment_id) REFERENCES scout_assignment(assignment_id)
);

CREATE TABLE IF NOT EXISTS youth_academy (
    club_id INTEGER PRIMARY KEY,
    youth_level INTEGER DEFAULT 50,
    training_level INTEGER DEFAULT 50,
    recruitment_range TEXT,
    academy_reputation INTEGER DEFAULT 50,
    academy_style TEXT,
    monthly_cost INTEGER DEFAULT 0,
    u18_coach_quality INTEGER DEFAULT 50,
    u21_coach_quality INTEGER DEFAULT 50,
    FOREIGN KEY(club_id) REFERENCES club(club_id)
);

CREATE TABLE IF NOT EXISTS staff (
    staff_id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    role TEXT,
    nationality TEXT,
    age INTEGER,
    current_club_id INTEGER,
    ability INTEGER DEFAULT 50,
    potential INTEGER DEFAULT 50,
    reputation INTEGER DEFAULT 50,
    salary INTEGER DEFAULT 0,
    contract_until TEXT,
    attributes_json TEXT,
    FOREIGN KEY(current_club_id) REFERENCES club(club_id)
);

CREATE TABLE IF NOT EXISTS agent (
    agent_id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    nationality TEXT,
    greed INTEGER DEFAULT 50,
    negotiation INTEGER DEFAULT 50,
    media_influence INTEGER DEFAULT 50,
    relationship_level INTEGER DEFAULT 50,
    style TEXT
);

CREATE TABLE IF NOT EXISTS player_agent (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_id INTEGER NOT NULL,
    agent_id INTEGER NOT NULL,
    FOREIGN KEY(player_id) REFERENCES player(player_id),
    FOREIGN KEY(agent_id) REFERENCES agent(agent_id)
);

CREATE TABLE IF NOT EXISTS historical_event (
    event_id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    event_type TEXT,
    trigger_date TEXT,
    trigger_conditions_json TEXT,
    choices_json TEXT,
    effects_json TEXT,
    is_historical INTEGER DEFAULT 1
);

CREATE TABLE IF NOT EXISTS save_world_state (
    save_id INTEGER PRIMARY KEY AUTOINCREMENT,
    save_name TEXT NOT NULL,
    current_date TEXT NOT NULL,
    current_season_id INTEGER NOT NULL,
    manager_club_id INTEGER NOT NULL,
    mode TEXT,
    scenario_id TEXT,
    config_json TEXT,
    created_at TEXT,
    updated_at TEXT,
    FOREIGN KEY(current_season_id) REFERENCES season(season_id),
    FOREIGN KEY(manager_club_id) REFERENCES club(club_id)
);

CREATE TABLE IF NOT EXISTS save_player_state (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    save_id INTEGER NOT NULL,
    player_id INTEGER NOT NULL,
    current_club_id INTEGER,
    loan_club_id INTEGER,
    current_ca INTEGER DEFAULT 50,
    current_pa INTEGER DEFAULT 50,
    condition INTEGER DEFAULT 100,
    morale INTEGER DEFAULT 50,
    injury_status TEXT DEFAULT 'healthy',
    injury_until TEXT,
    contract_until TEXT,
    wage INTEGER DEFAULT 0,
    market_value INTEGER DEFAULT 0,
    career_status TEXT DEFAULT 'active',
    squad_role TEXT,
    FOREIGN KEY(save_id) REFERENCES save_world_state(save_id),
    FOREIGN KEY(player_id) REFERENCES player(player_id)
);

CREATE TABLE IF NOT EXISTS save_club_state (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    save_id INTEGER NOT NULL,
    club_id INTEGER NOT NULL,
    balance INTEGER DEFAULT 0,
    transfer_budget INTEGER DEFAULT 0,
    wage_budget INTEGER DEFAULT 0,
    reputation INTEGER DEFAULT 50,
    board_satisfaction INTEGER DEFAULT 50,
    fan_satisfaction INTEGER DEFAULT 50,
    dressing_room_morale INTEGER DEFAULT 50,
    FOREIGN KEY(save_id) REFERENCES save_world_state(save_id),
    FOREIGN KEY(club_id) REFERENCES club(club_id)
);

CREATE TABLE IF NOT EXISTS save_news (
    news_id INTEGER PRIMARY KEY AUTOINCREMENT,
    save_id INTEGER NOT NULL,
    news_date TEXT,
    title TEXT,
    body TEXT,
    news_type TEXT,
    related_player_id INTEGER,
    related_club_id INTEGER,
    is_read INTEGER DEFAULT 0,
    FOREIGN KEY(save_id) REFERENCES save_world_state(save_id)
);

CREATE TABLE IF NOT EXISTS save_injury (
    injury_id INTEGER PRIMARY KEY AUTOINCREMENT,
    save_id INTEGER NOT NULL,
    player_id INTEGER NOT NULL,
    injury_type TEXT,
    start_date TEXT,
    expected_return_date TEXT,
    severity INTEGER,
    recurrence_risk INTEGER DEFAULT 0,
    status TEXT DEFAULT 'active',
    FOREIGN KEY(save_id) REFERENCES save_world_state(save_id),
    FOREIGN KEY(player_id) REFERENCES player(player_id)
);

CREATE TABLE IF NOT EXISTS save_transfer_offer (
    offer_id INTEGER PRIMARY KEY AUTOINCREMENT,
    save_id INTEGER NOT NULL,
    player_id INTEGER NOT NULL,
    from_club_id INTEGER,
    to_club_id INTEGER,
    offer_type TEXT,
    fee INTEGER DEFAULT 0,
    wage_offer INTEGER DEFAULT 0,
    contract_years INTEGER,
    status TEXT DEFAULT 'pending',
    created_date TEXT,
    expires_date TEXT,
    FOREIGN KEY(save_id) REFERENCES save_world_state(save_id),
    FOREIGN KEY(player_id) REFERENCES player(player_id)
);

CREATE INDEX IF NOT EXISTS idx_player_name ON player(real_name);
CREATE INDEX IF NOT EXISTS idx_player_nat ON player(nationality);
CREATE INDEX IF NOT EXISTS idx_player_pos ON player(primary_position);
CREATE INDEX IF NOT EXISTS idx_attr_player_season ON player_attributes(player_id, season_id);
CREATE INDEX IF NOT EXISTS idx_squad_season_club ON squad_membership(season_id, club_id);
CREATE INDEX IF NOT EXISTS idx_transfer_player ON transfer_history(player_id);
CREATE INDEX IF NOT EXISTS idx_transfer_date ON transfer_history(transfer_date);
CREATE INDEX IF NOT EXISTS idx_match_date ON match(match_date);
CREATE INDEX IF NOT EXISTS idx_match_competition ON match(season_id, competition_id);
CREATE INDEX IF NOT EXISTS idx_save_player ON save_player_state(save_id, player_id);
CREATE INDEX IF NOT EXISTS idx_scout_report ON scout_report(save_id, player_id);
