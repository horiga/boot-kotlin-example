CREATE TABLE IF NOT EXISTS `user` (
  `id` VARCHAR(50) NOT NULL PRIMARY KEY,
  `name` VARCHAR(100) NOT NULL,
  `description` VARCHAR(512) NULL,
  `birthday` DATE NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- initial data
INSERT INTO user(`id`,`name`,`description`,`birthday`) VALUES('test0001','horiga','this is a test user','2000-01-01');