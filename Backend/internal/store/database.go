package store

import (
	"fmt"
	"log"
	"os"
	"time"

	"github.com/joho/godotenv"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

func Open() (*gorm.DB, error) {
	_ = godotenv.Load(".env", "Backend/.env")

	dsn := os.Getenv("DATABASE_URL")
	if dsn == "" {
		host := os.Getenv("DB_HOST")
		if host == "" {
			host = "127.0.0.1"
		}
		port := os.Getenv("DB_PORT")
		if port == "" {
			port = "5432"
		}
		user := os.Getenv("DB_USER")
		if user == "" {
			user = os.Getenv("USER")
		}
		if user == "" {
			user = "postgres"
		}
		password := os.Getenv("DB_PASSWORD")
		dbname := os.Getenv("DB_NAME")
		if dbname == "" {
			dbname = "pguard_db"
		}
		dsn = fmt.Sprintf("host=%s user=%s password=%s dbname=%s port=%s sslmode=disable", host, user, password, dbname, port)
	}

	gormLogger := logger.New(
		log.New(os.Stdout, "\r\n", log.LstdFlags),
		logger.Config{
			SlowThreshold:             200 * time.Millisecond,
			LogLevel:                  logger.Warn,
			IgnoreRecordNotFoundError: true,
			Colorful:                  true,
		},
	)

	db, err := gorm.Open(postgres.Open(dsn), &gorm.Config{
		Logger: gormLogger,
	})
	if err != nil {
		return nil, fmt.Errorf("error connecting to postgres: %w", err)
	}

	if sqlDB, err := db.DB(); err == nil {
		sqlDB.SetMaxOpenConns(25)
		sqlDB.SetMaxIdleConns(10)
		sqlDB.SetConnMaxLifetime(5 * time.Minute)
	}

	fmt.Println("connected to database")
	return db, nil
}

func AutoMigrate(db *gorm.DB) error {
	return db.AutoMigrate(
		&User{},
		&RefreshToken{},
		&UserSettings{},
		&BlocklistItem{},
		&Streak{},
		&RelapseLog{},
		&Habit{},
		&HabitLog{},
		&AccountabilityPartner{},
		&SecurityAlert{},
		&UnlockCode{},
		&TimeDelayUnlock{},
	)
}
