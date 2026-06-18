/**
 * Database migrations — initialize user preferences and conditions tables
 * These are run on app startup to ensure required tables exist
 */
const { pool } = require('./index');

/**
 * Run all pending migrations
 */
async function runMigrations() {
  try {
    console.log('[migrations] Starting database migrations...');

    // Ensure user_preferences table exists
    await pool.query(`
      CREATE TABLE IF NOT EXISTS user_preferences (
        id SERIAL PRIMARY KEY,
        user_id INT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
        
        language_style VARCHAR(20) DEFAULT 'clinical',
        tone VARCHAR(20) DEFAULT 'direct',
        tracking_depth VARCHAR(20) DEFAULT 'moderate',
        insight_frequency VARCHAR(20) DEFAULT 'daily',
        
        show_why BOOLEAN DEFAULT true,
        show_what BOOLEAN DEFAULT true,
        show_meaning BOOLEAN DEFAULT true,
        show_action BOOLEAN DEFAULT true,
        
        onboarded BOOLEAN DEFAULT false,
        preferences_version VARCHAR(10) DEFAULT '1.0',
        
        created_at TIMESTAMP DEFAULT NOW(),
        updated_at TIMESTAMP DEFAULT NOW()
      );
      
      CREATE INDEX IF NOT EXISTS idx_user_preferences_user_id 
        ON user_preferences(user_id);
    `);

    console.log('[migrations] user_preferences table created/verified');

    // Ensure user_conditions table exists
    await pool.query(`
      CREATE TABLE IF NOT EXISTS user_conditions (
        id SERIAL PRIMARY KEY,
        user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        condition_name VARCHAR(50) NOT NULL,
        created_at TIMESTAMP DEFAULT NOW(),
        UNIQUE(user_id, condition_name)
      );
      
      CREATE INDEX IF NOT EXISTS idx_user_conditions_user_id 
        ON user_conditions(user_id);
    `);

    console.log('[migrations] user_conditions table created/verified');

    console.log('[migrations] All migrations completed successfully');
  } catch (err) {
    console.error('[migrations] Fatal error:', err.message);
    // Don't crash the app — migrations might fail in read-only environments
    // but non-db operations should still work
  }
}

module.exports = { runMigrations };
