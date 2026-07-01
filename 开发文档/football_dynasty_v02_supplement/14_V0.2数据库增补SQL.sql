-- 《绿茵王朝：2002》V0.2 数据库增补 SQL
-- 用途：补充合规、数据包、AI、经济、蝴蝶效应、性能日志、迁移等表结构。

CREATE TABLE IF NOT EXISTS data_pack_manifest (
    pack_id TEXT PRIMARY KEY,
    pack_name TEXT NOT NULL,
    pack_type TEXT NOT NULL,
    schema_version INTEGER NOT NULL,
    data_version TEXT NOT NULL,
    distribution TEXT NOT NULL,
    contains_real_names INTEGER DEFAULT 0,
    contains_real_logos INTEGER DEFAULT 0,
    contains_real_faces INTEGER DEFAULT 0,
    license_note TEXT,
    checksum TEXT,
    created_at TEXT,
    updated_at TEXT
);

CREATE TABLE IF NOT EXISTS save_manifest (
    save_id TEXT PRIMARY KEY,
    game_version TEXT NOT NULL,
    schema_version INTEGER NOT NULL,
    data_pack_id TEXT,
    data_pack_version TEXT,
    current_date TEXT,
    created_at TEXT,
    last_played_at TEXT,
    last_checkpoint_id TEXT
);

CREATE TABLE IF NOT EXISTS club_ai_profile (
    club_id INTEGER PRIMARY KEY,
    ambition INTEGER DEFAULT 50,
    financial_power INTEGER DEFAULT 50,
    youth_preference INTEGER DEFAULT 50,
    star_preference INTEGER DEFAULT 50,
    resale_preference INTEGER DEFAULT 50,
    domestic_preference INTEGER DEFAULT 50,
    tactical_identity TEXT,
    risk_tolerance INTEGER DEFAULT 50,
    wage_strictness INTEGER DEFAULT 50,
    patience_with_manager INTEGER DEFAULT 50
);

CREATE TABLE IF NOT EXISTS ai_decision_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    save_id TEXT NOT NULL,
    club_id INTEGER NOT NULL,
    decision_date TEXT NOT NULL,
    decision_type TEXT NOT NULL,
    target_player_id INTEGER,
    score REAL,
    reason TEXT,
    budget_before INTEGER,
    budget_after INTEGER,
    result TEXT
);

CREATE TABLE IF NOT EXISTS economy_index (
    year INTEGER PRIMARY KEY,
    global_index REAL NOT NULL,
    transfer_fee_index REAL NOT NULL,
    wage_index REAL NOT NULL,
    commercial_index REAL NOT NULL
);

CREATE TABLE IF NOT EXISTS league_economy_profile (
    league_id INTEGER PRIMARY KEY,
    base_multiplier REAL NOT NULL,
    growth_rate REAL NOT NULL,
    volatility REAL DEFAULT 0.0,
    notes TEXT
);

CREATE TABLE IF NOT EXISTS butterfly_event (
    event_id TEXT PRIMARY KEY,
    save_id TEXT NOT NULL,
    trigger_type TEXT NOT NULL,
    source_player_id INTEGER,
    source_club_id INTEGER,
    expected_club_id INTEGER,
    trigger_date TEXT NOT NULL,
    importance INTEGER NOT NULL,
    impact_budget INTEGER NOT NULL,
    max_depth INTEGER DEFAULT 3,
    status TEXT DEFAULT 'pending',
    summary TEXT
);

CREATE TABLE IF NOT EXISTS butterfly_impact_node (
    node_id TEXT PRIMARY KEY,
    event_id TEXT NOT NULL,
    depth INTEGER NOT NULL,
    impact_type TEXT NOT NULL,
    target_club_id INTEGER,
    target_player_id INTEGER,
    impact_strength REAL NOT NULL,
    status TEXT DEFAULT 'pending',
    result_summary TEXT,
    FOREIGN KEY(event_id) REFERENCES butterfly_event(event_id)
);

CREATE TABLE IF NOT EXISTS local_data_issue (
    issue_id INTEGER PRIMARY KEY AUTOINCREMENT,
    pack_id TEXT,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    issue_type TEXT NOT NULL,
    description TEXT,
    status TEXT DEFAULT 'open',
    created_at TEXT,
    resolved_at TEXT
);

CREATE TABLE IF NOT EXISTS perf_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    save_id TEXT,
    log_date TEXT,
    action_type TEXT NOT NULL,
    duration_ms INTEGER NOT NULL,
    memory_mb INTEGER,
    db_size_mb REAL,
    extra_json TEXT
);

CREATE TABLE IF NOT EXISTS season_archive (
    archive_id INTEGER PRIMARY KEY AUTOINCREMENT,
    save_id TEXT NOT NULL,
    season_id INTEGER NOT NULL,
    archive_type TEXT NOT NULL,
    summary_json TEXT,
    created_at TEXT
);

CREATE TABLE IF NOT EXISTS checkpoint (
    checkpoint_id TEXT PRIMARY KEY,
    save_id TEXT NOT NULL,
    checkpoint_type TEXT NOT NULL,
    checkpoint_date TEXT NOT NULL,
    file_path TEXT,
    checksum TEXT,
    created_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_ai_decision_log_save_club ON ai_decision_log(save_id, club_id);
CREATE INDEX IF NOT EXISTS idx_butterfly_event_save ON butterfly_event(save_id, status);
CREATE INDEX IF NOT EXISTS idx_butterfly_node_event ON butterfly_impact_node(event_id, depth);
CREATE INDEX IF NOT EXISTS idx_perf_log_save_action ON perf_log(save_id, action_type);
CREATE INDEX IF NOT EXISTS idx_local_data_issue_entity ON local_data_issue(entity_type, entity_id);
