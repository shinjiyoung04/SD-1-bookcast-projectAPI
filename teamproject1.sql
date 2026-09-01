

CREATE DATABASE IF NOT EXISTS teamproject1
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE teamproject1;


/*
=========================================================
 1. 도서관
=========================================================
*/

CREATE TABLE IF NOT EXISTS libraries (
    library_id BIGINT PRIMARY KEY AUTO_INCREMENT,

   
    lib_code VARCHAR(50) NULL,

    library_name VARCHAR(150) NOT NULL,
    address VARCHAR(255) NULL,
    phone VARCHAR(50) NULL,

    created_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_libraries_lib_code
        UNIQUE (lib_code),

    INDEX idx_libraries_name (
        library_name
    )
) ENGINE = InnoDB;


/*
=========================================================
 2. 사용자
=========================================================
*/

CREATE TABLE IF NOT EXISTS users (
    user_id BIGINT PRIMARY KEY AUTO_INCREMENT,

    login_id VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,

    name VARCHAR(50) NOT NULL,
    nickname VARCHAR(50) NULL,
    email VARCHAR(100) NULL,

    profile_image_url VARCHAR(500) NULL,

    address VARCHAR(255) NULL,
    birth_date DATE NULL,
    gender VARCHAR(20) NULL,

    role ENUM(
        'USER',
        'ADMIN',
        'MASTER_ADMIN'
    ) NOT NULL DEFAULT 'USER',

    status ENUM(
        'ACTIVE',
        'DELETED',
        'BLOCKED'
    ) NOT NULL DEFAULT 'ACTIVE',

    /*
     * 관리자 담당 도서관
     *
     * managed_library_id:
     * 내부 libraries.library_id
     *
     * managed_library_code:
     * 정보나루 libCode
     */
    managed_library_id BIGINT NULL,

    managed_library_code VARCHAR(50) NULL
        COMMENT '정보나루 관리 도서관 코드',

    /*
     * LOCAL, KAKAO 등
     */
    provider VARCHAR(20) NULL,
    provider_id VARCHAR(100) NULL,

    created_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_users_login_id
        UNIQUE (login_id),

    CONSTRAINT uk_users_email
        UNIQUE (email),

    CONSTRAINT fk_users_managed_library
        FOREIGN KEY (managed_library_id)
        REFERENCES libraries (library_id),

    INDEX idx_users_status_role_created (
        status,
        role,
        created_at
    ),

    INDEX idx_users_provider_id (
        provider,
        provider_id
    )
) ENGINE = InnoDB;


/*
=========================================================
 3. 카테고리
=========================================================
*/

CREATE TABLE IF NOT EXISTS categories (
    category_id BIGINT PRIMARY KEY AUTO_INCREMENT,

    category_name VARCHAR(50) NOT NULL,

    parent_id BIGINT NULL,

    created_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_categories_parent
        FOREIGN KEY (parent_id)
        REFERENCES categories (category_id),

    INDEX idx_categories_parent (
        parent_id
    )
) ENGINE = InnoDB;


/*
=========================================================
 4. 사용자 선호 카테고리
=========================================================
*/

CREATE TABLE IF NOT EXISTS user_preferences (
    preference_id BIGINT PRIMARY KEY AUTO_INCREMENT,

    user_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,

    created_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_user_preferences
        UNIQUE (
            user_id,
            category_id
        ),

    CONSTRAINT fk_user_preferences_user
        FOREIGN KEY (user_id)
        REFERENCES users (user_id),

    CONSTRAINT fk_user_preferences_category
        FOREIGN KEY (category_id)
        REFERENCES categories (category_id)
) ENGINE = InnoDB;


/*
=========================================================
 5. 도서
=========================================================
*/

CREATE TABLE IF NOT EXISTS books (
    book_id BIGINT PRIMARY KEY AUTO_INCREMENT,

    isbn VARCHAR(20) NOT NULL,

    title VARCHAR(255) NOT NULL,
    author VARCHAR(150) NOT NULL,
    publisher VARCHAR(150) NULL,
    published_date DATE NULL,

    category_id BIGINT NULL,

    description TEXT NULL,
    thumbnail_url VARCHAR(500) NULL,

    view_count INT NOT NULL DEFAULT 0,

    average_rating DECIMAL(3, 2)
        NOT NULL DEFAULT 0,

    created_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_books_isbn
        UNIQUE (isbn),

    CONSTRAINT fk_books_category
        FOREIGN KEY (category_id)
        REFERENCES categories (category_id),

    INDEX idx_books_title (
        title
    ),

    INDEX idx_books_author (
        author
    ),

    INDEX idx_books_category (
        category_id
    )
) ENGINE = InnoDB;


/*
=========================================================
 6. 도서관 소장 도서
=========================================================
*/

CREATE TABLE IF NOT EXISTS library_books (
    library_book_id BIGINT
        PRIMARY KEY AUTO_INCREMENT,

    library_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,

    is_owned TINYINT(1)
        NOT NULL DEFAULT 1,

    loan_status ENUM(
        'AVAILABLE',
        'LOANED',
        'RESERVED',
        'UNKNOWN'
    ) NOT NULL DEFAULT 'AVAILABLE',

    total_count INT NOT NULL DEFAULT 1,
    available_count INT NOT NULL DEFAULT 1,

    created_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_library_books
        UNIQUE (
            library_id,
            book_id
        ),

    CONSTRAINT fk_library_books_library
        FOREIGN KEY (library_id)
        REFERENCES libraries (library_id),

    CONSTRAINT fk_library_books_book
        FOREIGN KEY (book_id)
        REFERENCES books (book_id),

    INDEX idx_library_books_book (
        book_id
    ),

    INDEX idx_library_books_loan (
        library_id,
        loan_status
    )
) ENGINE = InnoDB;


/*
=========================================================
 7. 도서 리뷰
=========================================================
*/

CREATE TABLE IF NOT EXISTS book_reviews (
    review_id BIGINT PRIMARY KEY AUTO_INCREMENT,

    book_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,

    content TEXT NOT NULL,
    rating DECIMAL(2, 1) NULL,

    is_deleted TINYINT(1)
        NOT NULL DEFAULT 0,

    created_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_book_reviews_book
        FOREIGN KEY (book_id)
        REFERENCES books (book_id),

    CONSTRAINT fk_book_reviews_user
        FOREIGN KEY (user_id)
        REFERENCES users (user_id),

    INDEX idx_book_reviews_book_visible (
        book_id,
        is_deleted,
        created_at
    ),

    INDEX idx_book_reviews_user (
        user_id,
        created_at
    )
) ENGINE = InnoDB;


/*
=========================================================
 8. 도서 조회 기록
=========================================================
*/

CREATE TABLE IF NOT EXISTS user_book_logs (
    log_id BIGINT PRIMARY KEY AUTO_INCREMENT,

    user_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,

    viewed_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_user_book_logs_user
        FOREIGN KEY (user_id)
        REFERENCES users (user_id),

    CONSTRAINT fk_user_book_logs_book
        FOREIGN KEY (book_id)
        REFERENCES books (book_id),

    INDEX idx_user_book_logs_user_time (
        user_id,
        viewed_at
    ),

    INDEX idx_user_book_logs_book_time (
        book_id,
        viewed_at
    )
) ENGINE = InnoDB;


/*
=========================================================
 9. 희망도서 신청
=========================================================
*/

CREATE TABLE IF NOT EXISTS hope_applications (
    application_id BIGINT
        PRIMARY KEY AUTO_INCREMENT,

    user_id BIGINT NOT NULL,

    /*
     * 내부 books 테이블에 등록되지 않은 외부 도서라면
     * NULL이 될 수 있습니다.
     */
    book_id BIGINT NULL,

    isbn VARCHAR(20) NOT NULL,
    title VARCHAR(255) NOT NULL,
    author VARCHAR(150) NOT NULL,

    category_id BIGINT NULL,

    
    library_id BIGINT NOT NULL,

    reason TEXT NOT NULL,

    status ENUM(
        'PENDING',
        'APPROVED',
        'REJECTED',
        'CANCELED'
    ) NOT NULL DEFAULT 'PENDING',

    admin_id BIGINT NULL,
    admin_comment TEXT NULL,

    created_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    processed_at TIMESTAMP NULL,

    /*
     * 탈퇴 회원 등의 신청을 일반 화면에서 숨길 때 사용
     */
    is_hidden TINYINT(1)
        NOT NULL DEFAULT 0,

    CONSTRAINT fk_hope_applications_user
        FOREIGN KEY (user_id)
        REFERENCES users (user_id),

    CONSTRAINT fk_hope_applications_book
        FOREIGN KEY (book_id)
        REFERENCES books (book_id),

    CONSTRAINT fk_hope_applications_category
        FOREIGN KEY (category_id)
        REFERENCES categories (category_id),

    CONSTRAINT fk_hope_applications_library
        FOREIGN KEY (library_id)
        REFERENCES libraries (library_id),

    CONSTRAINT fk_hope_applications_admin
        FOREIGN KEY (admin_id)
        REFERENCES users (user_id),

    /*
     * 전체 및 공개 신청 조회
     */
    INDEX idx_hope_applications_public (
        is_hidden,
        status,
        created_at
    ),

    /*
     * 일반 관리자 담당 도서관 신청 조회
     */
    INDEX idx_hope_applications_admin (
        library_id,
        is_hidden,
        status,
        created_at
    ),

    /*
     * 사용자 본인의 신청 내역 조회
     */
    INDEX idx_hope_applications_user (
        user_id,
        created_at
    )
) ENGINE = InnoDB;


/*
=========================================================
 10. 희망도서 현재 투표 상태
=========================================================
*/

CREATE TABLE IF NOT EXISTS hope_votes (
    vote_id BIGINT PRIMARY KEY AUTO_INCREMENT,

    application_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,

    active TINYINT(1)
        NOT NULL DEFAULT 1,

    voted_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    canceled_at TIMESTAMP NULL,

    created_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_hope_votes
        UNIQUE (
            application_id,
            user_id
        ),

    CONSTRAINT fk_hope_votes_application
        FOREIGN KEY (application_id)
        REFERENCES hope_applications (application_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_hope_votes_user
        FOREIGN KEY (user_id)
        REFERENCES users (user_id)
        ON DELETE CASCADE,

    INDEX idx_hope_votes_application_active (
        application_id,
        active
    ),

    INDEX idx_hope_votes_user_active (
        user_id,
        active
    ),

    INDEX idx_hope_votes_voted_at (
        voted_at
    )
) ENGINE = InnoDB;


/*
=========================================================
 11. 희망도서 투표 이력
=========================================================
*/

CREATE TABLE IF NOT EXISTS hope_vote_events (
    event_id BIGINT PRIMARY KEY AUTO_INCREMENT,

    application_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,

    event_type ENUM(
        'VOTE',
        'CANCEL'
    ) NOT NULL,

    created_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_hope_vote_events_application
        FOREIGN KEY (application_id)
        REFERENCES hope_applications (application_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_hope_vote_events_user
        FOREIGN KEY (user_id)
        REFERENCES users (user_id)
        ON DELETE CASCADE,

    INDEX idx_hope_vote_events_application (
        application_id,
        created_at
    ),

    INDEX idx_hope_vote_events_user (
        user_id,
        created_at
    ),

    INDEX idx_hope_vote_events_type (
        event_type,
        created_at
    )
) ENGINE = InnoDB;


/*
=========================================================
 12. 외부 도서 통계
=========================================================
*/

CREATE TABLE IF NOT EXISTS book_external_stats (
    stat_id BIGINT PRIMARY KEY AUTO_INCREMENT,

    book_id BIGINT NULL,

    isbn VARCHAR(20) NOT NULL,

    sales_index INT NOT NULL DEFAULT 0,
    review_count INT NOT NULL DEFAULT 0,

    average_rating DECIMAL(3, 2)
        NOT NULL DEFAULT 0,

    loan_count INT NOT NULL DEFAULT 0,
    search_count INT NOT NULL DEFAULT 0,

    collected_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_book_external_stats_book
        FOREIGN KEY (book_id)
        REFERENCES books (book_id),

    INDEX idx_book_external_stats_book (
        book_id,
        collected_at
    ),

    INDEX idx_book_external_stats_isbn (
        isbn,
        collected_at
    )
) ENGINE = InnoDB;


/*
=========================================================
 13. AI 승인률 예측
=========================================================
*/

CREATE TABLE IF NOT EXISTS ai_predictions (
    prediction_id BIGINT
        PRIMARY KEY AUTO_INCREMENT,

    application_id BIGINT NULL,
    book_id BIGINT NULL,

    approval_probability DECIMAL(5, 2)
        NOT NULL
        COMMENT 'AI 예측 승인 확률',

    popularity_score DECIMAL(5, 2)
        NOT NULL
        COMMENT 'AI 인기도 점수',

    vote_adjustment DECIMAL(5, 2)
        NOT NULL DEFAULT 0
        COMMENT '주민 투표 보정값',

    final_score DECIMAL(5, 2)
        NOT NULL
        COMMENT '최종 추천 점수',

    model_version VARCHAR(50) NULL,

    created_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ai_predictions_application
        FOREIGN KEY (application_id)
        REFERENCES hope_applications (application_id),

    CONSTRAINT fk_ai_predictions_book
        FOREIGN KEY (book_id)
        REFERENCES books (book_id),

    INDEX idx_ai_predictions_application (
        application_id,
        created_at
    ),

    INDEX idx_ai_predictions_book (
        book_id,
        created_at
    )
) ENGINE = InnoDB;


/*
=========================================================
 14. 관리자 활동 로그
=========================================================
*/

CREATE TABLE IF NOT EXISTS admin_activity_logs (
    admin_log_id BIGINT
        PRIMARY KEY AUTO_INCREMENT,

    admin_id BIGINT NOT NULL,

    target_type VARCHAR(50) NOT NULL
        COMMENT 'BOOK, APPLICATION, USER 등',

    target_id BIGINT NOT NULL,

    action VARCHAR(100) NOT NULL,

    description TEXT NULL,

    created_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_admin_activity_logs_admin
        FOREIGN KEY (admin_id)
        REFERENCES users (user_id),

    INDEX idx_admin_activity_logs_admin (
        admin_id,
        created_at
    ),

    INDEX idx_admin_activity_logs_target (
        target_type,
        target_id,
        created_at
    )
) ENGINE = InnoDB;


/*
=========================================================
 15. 관리자 등업 신청
=========================================================
*/

CREATE TABLE IF NOT EXISTS promotion_requests (
    request_id BIGINT
        PRIMARY KEY AUTO_INCREMENT,

    user_id BIGINT NOT NULL,

    library_name VARCHAR(150) NOT NULL,


    library_code VARCHAR(50) NOT NULL,

    department VARCHAR(100) NOT NULL,
    employee_number VARCHAR(100) NOT NULL,
    contact VARCHAR(50) NOT NULL,

    reason TEXT NOT NULL,

    status ENUM(
        'PENDING',
        'APPROVED',
        'REJECTED',
        'CANCELED'
    ) NOT NULL DEFAULT 'PENDING',

    master_admin_id BIGINT NULL,
    master_comment TEXT NULL,

    created_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    processed_at TIMESTAMP NULL,

    updated_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_promotion_requests_user
        FOREIGN KEY (user_id)
        REFERENCES users (user_id),

    CONSTRAINT fk_promotion_requests_master
        FOREIGN KEY (master_admin_id)
        REFERENCES users (user_id),

    
    INDEX idx_promotion_requests_user (
        user_id,
        created_at
    ),

    INDEX idx_promotion_requests_status (
        status,
        created_at
    )
) ENGINE = InnoDB;

USE teamproject1;

CREATE TABLE IF NOT EXISTS book_likes (
    like_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    isbn VARCHAR(20) NOT NULL,
    title VARCHAR(255) NOT NULL,
    author VARCHAR(150) NULL,
    publisher VARCHAR(150) NULL,
    thumbnail_url VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_book_likes_user_isbn
        UNIQUE (user_id, isbn),

    CONSTRAINT fk_book_likes_user
        FOREIGN KEY (user_id)
        REFERENCES users (user_id)
        ON DELETE CASCADE,

    INDEX idx_book_likes_user_created (
        user_id,
        created_at
    ),

    INDEX idx_book_likes_isbn (
        isbn
    )
) ENGINE = InnoDB;

USE teamproject1;

CREATE TABLE IF NOT EXISTS book_likes (
    like_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    isbn VARCHAR(20) NOT NULL,
    title VARCHAR(255) NOT NULL,
    author VARCHAR(150) NOT NULL,
    publisher VARCHAR(150) NULL,
    thumbnail_url VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_book_likes_user_isbn
        UNIQUE (user_id, isbn),

    CONSTRAINT fk_book_likes_user
        FOREIGN KEY (user_id)
        REFERENCES users (user_id)
        ON DELETE CASCADE,

    INDEX idx_book_likes_user_created (
        user_id,
        created_at
    ),

    INDEX idx_book_likes_isbn (
        isbn
    )
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS book_wishlists (
    wishlist_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    isbn VARCHAR(20) NOT NULL,
    title VARCHAR(255) NOT NULL,
    author VARCHAR(150) NOT NULL,
    publisher VARCHAR(150) NULL,
    thumbnail_url VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_book_wishlists_user_isbn
        UNIQUE (user_id, isbn),

    CONSTRAINT fk_book_wishlists_user
        FOREIGN KEY (user_id)
        REFERENCES users (user_id)
        ON DELETE CASCADE,

    INDEX idx_book_wishlists_user_created (
        user_id,
        created_at
    ),

    INDEX idx_book_wishlists_isbn (
        isbn
    )
) ENGINE = InnoDB;


USE teamproject1;

CREATE INDEX idx_hope_applications_duplicate_check
ON hope_applications (
    library_id,
    isbn,
    status,
    is_hidden
);


DROP TRIGGER IF EXISTS
trg_hope_applications_prevent_duplicate;

DELIMITER $$

CREATE TRIGGER
trg_hope_applications_prevent_duplicate
BEFORE INSERT ON hope_applications
FOR EACH ROW
BEGIN
    IF
        NEW.library_id IS NOT NULL
        AND COALESCE(NEW.is_hidden, FALSE) = FALSE
        AND NEW.status = 'PENDING'
        AND EXISTS (
            SELECT 1
            FROM hope_applications existing_application
            WHERE existing_application.library_id = NEW.library_id
              AND REPLACE(
                    REPLACE(existing_application.isbn, '-', ''),
                    ' ',
                    ''
                  ) =
                  REPLACE(
                    REPLACE(NEW.isbn, '-', ''),
                    ' ',
                    ''
                  )
              AND existing_application.status = 'PENDING'
              AND existing_application.is_hidden = FALSE
        )
    THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'DUPLICATE_HOPE_APPLICATION';
    END IF;
END$$

DELIMITER ;

USE teamproject1;


CREATE TABLE IF NOT EXISTS library_daily_statistics (
    statistic_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    library_id BIGINT NOT NULL,

    stat_date DATE NOT NULL,

    visitor_count INT NOT NULL DEFAULT 0,

    active_borrower_count INT NOT NULL DEFAULT 0,

    program_participant_count INT NOT NULL DEFAULT 0,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_library_daily_statistics_library
        FOREIGN KEY (library_id)
        REFERENCES libraries(library_id),

    CONSTRAINT uq_library_daily_statistics
        UNIQUE (library_id, stat_date),

    INDEX idx_library_daily_statistics_date (
        library_id,
        stat_date
    )
);


CREATE TABLE IF NOT EXISTS library_genre_national_snapshot (
    snapshot_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    snapshot_date DATE NOT NULL,

    kdc_code VARCHAR(2) NOT NULL,

    holding_count BIGINT NOT NULL DEFAULT 0,

    participating_library_count INT NOT NULL DEFAULT 0,

    source_note VARCHAR(255),

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_library_genre_national_snapshot
        UNIQUE (
            snapshot_date,
            kdc_code
        ),

    INDEX idx_library_genre_national_snapshot_date (
        snapshot_date
    )
);


USE teamproject1;

/*
 * 1. 일반 관리자 담당 도서관 확인
 */
SELECT
    u.user_id,
    u.login_id,
    u.role,
    u.status,
    u.managed_library_id,
    u.managed_library_code,
    l.library_name AS managed_library_name,
    l.lib_code AS managed_library_lib_code
FROM users u
LEFT JOIN libraries l
  ON l.library_id =
     u.managed_library_id
WHERE u.role = 'ADMIN';


/*
 * 2. 승인 대기 신청과 연결 도서관 확인
 */
SELECT
    a.application_id,
    a.status,
    a.is_hidden,
    a.library_id,
    l.library_name,
    l.lib_code,
    a.title,
    a.created_at
FROM hope_applications a
LEFT JOIN libraries l
  ON l.library_id =
     a.library_id
WHERE UPPER(
          CAST(
              a.status AS CHAR
          )
      ) = 'PENDING'
ORDER BY
    a.created_at DESC,
    a.application_id DESC;


/*
 * 3. 관리자와 신청 도서관 매칭 여부 확인
 *
 * 아래 @admin_user_id를 실제 일반 관리자 번호로 변경합니다.
 */
SET @admin_user_id = 1;

SELECT
    a.application_id,
    a.title,

    admin_user.managed_library_id,
    admin_user.managed_library_code,

    managed_library.library_name
        AS managed_library_name,

    managed_library.lib_code
        AS managed_library_lib_code,

    a.library_id
        AS application_library_id,

    application_library.library_name
        AS application_library_name,

    application_library.lib_code
        AS application_library_lib_code,

    CASE
        WHEN a.library_id =
             admin_user.managed_library_id
        THEN 'MATCH_BY_ID'

        WHEN TRIM(
                 COALESCE(
                     application_library.lib_code,
                     ''
                 )
             ) =
             TRIM(
                 COALESCE(
                     NULLIF(
                         admin_user.managed_library_code,
                         ''
                     ),
                     managed_library.lib_code,
                     ''
                 )
             )
        THEN 'MATCH_BY_CODE'

        WHEN TRIM(
                 COALESCE(
                     application_library.library_name,
                     ''
                 )
             ) =
             TRIM(
                 COALESCE(
                     managed_library.library_name,
                     ''
                 )
             )
        THEN 'MATCH_BY_NAME'

        ELSE 'NO_MATCH'
    END AS scope_match

FROM hope_applications a

JOIN users admin_user
  ON admin_user.user_id =
     @admin_user_id

LEFT JOIN libraries managed_library
  ON managed_library.library_id =
     admin_user.managed_library_id

LEFT JOIN libraries application_library
  ON application_library.library_id =
     a.library_id

WHERE UPPER(
          CAST(
              a.status AS CHAR
          )
      ) = 'PENDING'

ORDER BY
    a.created_at DESC,
    a.application_id DESC;


USE teamproject1;

SELECT
    a.application_id,
    a.isbn,
    a.title,
    a.status,
    a.category_id AS application_category_id,
    b.category_id AS book_category_id,
    COALESCE(
        application_category.category_name,
        book_category.category_name
    ) AS resolved_category_name
FROM hope_applications a
LEFT JOIN books b
  ON b.book_id = a.book_id
LEFT JOIN categories application_category
  ON application_category.category_id = a.category_id
LEFT JOIN categories book_category
  ON book_category.category_id = b.category_id
ORDER BY
    a.created_at DESC,
    a.application_id DESC;

SELECT
    a.application_id,
    a.isbn,
    a.title
FROM hope_applications a
LEFT JOIN books b
  ON b.book_id = a.book_id
WHERE a.status = 'PENDING'
  AND COALESCE(a.category_id, b.category_id) IS NULL
ORDER BY
    a.created_at DESC;


USE teamproject1;

INSERT INTO categories (category_name, parent_id, created_at)
SELECT '총류', NULL, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM categories
    WHERE category_name = '총류' AND parent_id IS NULL
);

INSERT INTO categories (category_name, parent_id, created_at)
SELECT '철학', NULL, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM categories
    WHERE category_name = '철학' AND parent_id IS NULL
);

INSERT INTO categories (category_name, parent_id, created_at)
SELECT '종교', NULL, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM categories
    WHERE category_name = '종교' AND parent_id IS NULL
);

INSERT INTO categories (category_name, parent_id, created_at)
SELECT '사회과학', NULL, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM categories
    WHERE category_name = '사회과학' AND parent_id IS NULL
);

INSERT INTO categories (category_name, parent_id, created_at)
SELECT '자연과학', NULL, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM categories
    WHERE category_name = '자연과학' AND parent_id IS NULL
);

INSERT INTO categories (category_name, parent_id, created_at)
SELECT '기술과학', NULL, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM categories
    WHERE category_name = '기술과학' AND parent_id IS NULL
);

INSERT INTO categories (category_name, parent_id, created_at)
SELECT '예술', NULL, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM categories
    WHERE category_name = '예술' AND parent_id IS NULL
);

INSERT INTO categories (category_name, parent_id, created_at)
SELECT '언어', NULL, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM categories
    WHERE category_name = '언어' AND parent_id IS NULL
);

INSERT INTO categories (category_name, parent_id, created_at)
SELECT '문학', NULL, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM categories
    WHERE category_name = '문학' AND parent_id IS NULL
);

INSERT INTO categories (category_name, parent_id, created_at)
SELECT '역사', NULL, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM categories
    WHERE category_name = '역사' AND parent_id IS NULL
);

SELECT category_id, category_name, parent_id
FROM categories
WHERE category_name IN (
    '총류', '철학', '종교', '사회과학', '자연과학',
    '기술과학', '예술', '언어', '문학', '역사'
)
ORDER BY category_id;


USE teamproject1;


SET @admin_user_id = 1;


/*
 * 1. 일반 관리자 담당 도서관 정보
 */
SELECT
    admin_user.user_id,
    admin_user.login_id,
    admin_user.role,
    admin_user.status,
    admin_user.managed_library_id,
    admin_user.managed_library_code,

    managed_library.library_name
        AS managed_library_name,

    managed_library.lib_code
        AS managed_library_lib_code

FROM users admin_user

LEFT JOIN libraries managed_library
  ON managed_library.library_id =
     admin_user.managed_library_id

WHERE admin_user.user_id =
      @admin_user_id;


/*
 * 2. 신청 도서관과 관리자 담당 도서관 매칭 확인
 */
SELECT
    application.application_id,
    application.status,
    application.title,

    application.library_id
        AS application_library_id,

    application_library.library_name
        AS application_library_name,

    application_library.lib_code
        AS application_library_code,

    admin_user.managed_library_id
        AS admin_library_id,

    COALESCE(
        NULLIF(
            TRIM(
                admin_user.managed_library_code
            ),
            ''
        ),
        NULLIF(
            TRIM(
                managed_library.lib_code
            ),
            ''
        )
    ) AS admin_library_code,

    managed_library.library_name
        AS admin_library_name,

    CASE
        WHEN application.library_id =
             admin_user.managed_library_id
        THEN 'MATCH_BY_ID'

        WHEN TRIM(
                 COALESCE(
                     application_library.lib_code,
                     ''
                 )
             ) =
             COALESCE(
                 NULLIF(
                     TRIM(
                         admin_user.managed_library_code
                     ),
                     ''
                 ),
                 NULLIF(
                     TRIM(
                         managed_library.lib_code
                     ),
                     ''
                 )
             )
        THEN 'MATCH_BY_CODE'

        WHEN TRIM(
                 COALESCE(
                     application_library.library_name,
                     ''
                 )
             ) =
             TRIM(
                 COALESCE(
                     managed_library.library_name,
                     ''
                 )
             )
        THEN 'MATCH_BY_NAME'

        ELSE 'NO_MATCH'
    END AS scope_match

FROM hope_applications application

JOIN users admin_user
  ON admin_user.user_id =
     @admin_user_id

LEFT JOIN libraries application_library
  ON application_library.library_id =
     application.library_id

LEFT JOIN libraries managed_library
  ON managed_library.library_id =
     admin_user.managed_library_id

WHERE application.is_hidden = FALSE

ORDER BY
    application.created_at DESC,
    application.application_id DESC;


/*
 * 3. 수정된 서비스와 같은 조건으로 조회되는 신청 확인
 */
SELECT
    application.application_id,
    application.status,
    application.title,
    application.library_id,
    application_library.library_name,
    application_library.lib_code

FROM hope_applications application

JOIN users admin_user
  ON admin_user.user_id =
     @admin_user_id

LEFT JOIN libraries application_library
  ON application_library.library_id =
     application.library_id

LEFT JOIN libraries managed_library
  ON managed_library.library_id =
     admin_user.managed_library_id

WHERE application.is_hidden = FALSE
  AND (
      application.library_id =
      admin_user.managed_library_id

      OR TRIM(
             COALESCE(
                 application_library.lib_code,
                 ''
             )
         ) =
         COALESCE(
             NULLIF(
                 TRIM(
                     admin_user.managed_library_code
                 ),
                 ''
             ),
             NULLIF(
                 TRIM(
                     managed_library.lib_code
                 ),
                 ''
             )
         )

      OR TRIM(
             COALESCE(
                 application_library.library_name,
                 ''
             )
         ) =
         TRIM(
             COALESCE(
                 managed_library.library_name,
                 ''
             )
         )
  )

ORDER BY
    application.created_at DESC,
    application.application_id DESC;


USE teamproject1;

/*
 * 동일 도서관 + 동일 ISBN + 상태 조회 성능 개선
 * 이미 인덱스가 존재해도 재실행할 수 있도록 조건부로 생성합니다.
 */
SET @duplicate_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'hope_applications'
      AND INDEX_NAME = 'idx_hope_applications_duplicate_check'
);

SET @create_duplicate_index_sql = IF(
    @duplicate_index_exists = 0,
    'CREATE INDEX idx_hope_applications_duplicate_check
       ON hope_applications (library_id, isbn, status, is_hidden)',
    'SELECT ''idx_hope_applications_duplicate_check already exists'' AS message'
);

PREPARE create_duplicate_index_statement
FROM @create_duplicate_index_sql;

EXECUTE create_duplicate_index_statement;

DEALLOCATE PREPARE create_duplicate_index_statement;


DROP TRIGGER IF EXISTS trg_hope_applications_prevent_duplicate;

DELIMITER $$

CREATE TRIGGER trg_hope_applications_prevent_duplicate
BEFORE INSERT ON hope_applications
FOR EACH ROW
BEGIN
    IF
        NEW.library_id IS NOT NULL
        AND COALESCE(NEW.is_hidden, FALSE) = FALSE
        AND NEW.status = 'PENDING'
        AND EXISTS (
            SELECT 1
            FROM hope_applications existing_application
            LEFT JOIN libraries existing_library
              ON existing_library.library_id =
                 existing_application.library_id
            LEFT JOIN libraries new_library
              ON new_library.library_id = NEW.library_id
            WHERE
                (
                    existing_application.library_id = NEW.library_id
                    OR (
                        existing_library.lib_code IS NOT NULL
                        AND new_library.lib_code IS NOT NULL
                        AND existing_library.lib_code = new_library.lib_code
                    )
                )
              AND UPPER(
                    REPLACE(
                        REPLACE(existing_application.isbn, '-', ''),
                        ' ',
                        ''
                    )
                  ) =
                  UPPER(
                    REPLACE(
                        REPLACE(NEW.isbn, '-', ''),
                        ' ',
                        ''
                    )
                  )
              AND existing_application.status = 'PENDING'
              AND COALESCE(existing_application.is_hidden, FALSE) = FALSE
        )
    THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'DUPLICATE_HOPE_APPLICATION';
    END IF;
END$$

DELIMITER ;

select * from users;
