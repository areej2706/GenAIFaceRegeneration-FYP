"""
Database configuration for Pencil2Pixel API
- Uses PostgreSQL in production (Leapcell)
- Uses SQLite for local development
"""
import os
from sqlalchemy import create_engine, Column, Integer, String, Text, LargeBinary, DateTime, MetaData, Table
from sqlalchemy.orm import sessionmaker, declarative_base
from sqlalchemy.sql import func

# Determine environment
IS_PRODUCTION = os.environ.get('FLASK_ENV') == 'production' or os.environ.get('DATABASE_URL')

# Database URL
if IS_PRODUCTION:
    # Production: Use PostgreSQL from environment variable
    DATABASE_URL = os.environ.get('DATABASE_URL')
    if not DATABASE_URL:
        raise ValueError("DATABASE_URL environment variable is required in production")
    
    # Handle both postgres:// and postgresql:// schemes
    if DATABASE_URL.startswith('postgres://'):
        DATABASE_URL = DATABASE_URL.replace('postgres://', 'postgresql://', 1)
    
    engine = create_engine(DATABASE_URL, pool_pre_ping=True)
else:
    # Local development: Use SQLite
    DATABASE_URL = 'sqlite:///pencil2pixel.db'
    engine = create_engine(DATABASE_URL, connect_args={'check_same_thread': False})

# Create session factory
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

# Create base class for models
Base = declarative_base()

# Define models
class User(Base):
    __tablename__ = 'users'
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    user_id = Column(String(36), unique=True, nullable=False, index=True)
    username = Column(String(100), unique=True, nullable=False, index=True)
    email = Column(String(255), unique=True, nullable=False, index=True)
    password_hash = Column(String(255), nullable=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())

class ImageHistory(Base):
    __tablename__ = 'image_history'
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    image_id = Column(String(36), unique=True, nullable=False, index=True)
    user_id = Column(String(36), nullable=False, index=True)
    original_filename = Column(String(255))
    generated_image = Column(LargeBinary, nullable=False)
    attributes = Column(Text)
    created_at = Column(DateTime(timezone=True), server_default=func.now())

def init_db():
    """Initialize database tables"""
    Base.metadata.create_all(bind=engine)
    print(f"Database initialized: {'PostgreSQL (Production)' if IS_PRODUCTION else 'SQLite (Local)'}")

def get_db():
    """Get database session"""
    db = SessionLocal()
    try:
        return db
    except Exception:
        db.close()
        raise

def close_db(db):
    """Close database session"""
    db.close()
