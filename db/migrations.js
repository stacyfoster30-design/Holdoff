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

    // Ensure user_contacts table exists
    await pool.query(`
      CREATE TABLE IF NOT EXISTS user_contacts (
        id SERIAL PRIMARY KEY,
        user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        name VARCHAR(255) NOT NULL,
        phone_number VARCHAR(20),
        is_favorited BOOLEAN DEFAULT false,
        last_messaged_at TIMESTAMP,
        created_at TIMESTAMP DEFAULT NOW(),
        updated_at TIMESTAMP DEFAULT NOW(),
        UNIQUE(user_id, phone_number)
      );
      
      CREATE INDEX IF NOT EXISTS idx_user_contacts_user_id 
        ON user_contacts(user_id);
      CREATE INDEX IF NOT EXISTS idx_user_contacts_phone 
        ON user_contacts(phone_number);
    `);

    console.log('[migrations] user_contacts table created/verified');

    // Ensure message_threads table exists
    await pool.query(`
      CREATE TABLE IF NOT EXISTS message_threads (
        id SERIAL PRIMARY KEY,
        user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        contact_id INT REFERENCES user_contacts(id) ON DELETE SET NULL,
        contact_phone VARCHAR(20),
        last_message_at TIMESTAMP,
        created_at TIMESTAMP DEFAULT NOW(),
        updated_at TIMESTAMP DEFAULT NOW()
      );
      
      CREATE INDEX IF NOT EXISTS idx_message_threads_user_id 
        ON message_threads(user_id);
      CREATE INDEX IF NOT EXISTS idx_message_threads_contact_id 
        ON message_threads(contact_id);
      CREATE INDEX IF NOT EXISTS idx_message_threads_last_message 
        ON message_threads(last_message_at DESC);
    `);

    console.log('[migrations] message_threads table created/verified');

    // Ensure messages table exists
    await pool.query(`
      CREATE TABLE IF NOT EXISTS messages (
        id SERIAL PRIMARY KEY,
        thread_id INT NOT NULL REFERENCES message_threads(id) ON DELETE CASCADE,
        sender_type VARCHAR(20) NOT NULL,
        body TEXT,
        external_id VARCHAR(255),
        timestamp TIMESTAMP,
        created_at TIMESTAMP DEFAULT NOW()
      );
      
      CREATE INDEX IF NOT EXISTS idx_messages_thread_id 
        ON messages(thread_id);
      CREATE INDEX IF NOT EXISTS idx_messages_timestamp 
        ON messages(timestamp DESC);
    `);

    console.log('[migrations] messages table created/verified');

    console.log('[migrations] All migrations completed successfully');
  } catch (err) {
    console.error('[migrations] Fatal error:', err.message);
    // Don't crash the app — migrations might fail in read-only environments
    // but non-db operations should still work
  }
}

module.exports = { runMigrations };
