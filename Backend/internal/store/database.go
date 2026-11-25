package store

import (
	"fmt"
	"log"
	"os"
	"strings"
	"time"

	"github.com/joho/godotenv"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

func cleanEnvValue(val string) string {
	return strings.Trim(strings.TrimSpace(val), `"'`)
}

func Open() (*gorm.DB, error) {
	for _, f := range []string{".env.local", "../.env.local", ".env", "Backend/.env"} {
		_ = godotenv.Load(f)
	}

	dsn := cleanEnvValue(os.Getenv("DATABASE_URL"))
	if dsn == "" {
		var envKeys []string
		for _, e := range os.Environ() {
			if k, _, ok := strings.Cut(e, "="); ok {
				envKeys = append(envKeys, k)
			}
		}
		log.Printf("WARN: DATABASE_URL is empty! Available env keys: %v", envKeys)
		dsn = defaultDSN()
	}

	return openWithDSN(dsn)
}

func defaultDSN() string {
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
	return fmt.Sprintf("host=%s user=%s password=%s dbname=%s port=%s sslmode=disable", host, user, password, dbname, port)
}

func openWithDSN(dsn string) (*gorm.DB, error) {
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
		sqlDB.SetMaxOpenConns(15)
		sqlDB.SetMaxIdleConns(2)
		sqlDB.SetConnMaxLifetime(5 * time.Minute)
		sqlDB.SetConnMaxIdleTime(1 * time.Minute)
	}

	fmt.Println("connected to database")
	return db, nil
}

func AutoMigrate(db *gorm.DB) error {
	migrateDB := db
	if unpooledDSN := cleanEnvValue(os.Getenv("DATABASE_URL_UNPOOLED")); unpooledDSN != "" && unpooledDSN != cleanEnvValue(os.Getenv("DATABASE_URL")) {
		if directDB, err := openWithDSN(unpooledDSN); err == nil {
			migrateDB = directDB
			if sqlDB, err := directDB.DB(); err == nil {
				defer sqlDB.Close()
			}
		}
	}
	return migrateDB.AutoMigrate(
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
