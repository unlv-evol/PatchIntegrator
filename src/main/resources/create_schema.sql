-- MySQL Script generated by MySQL Workbench
-- Sun 23 Sep 2018 05:21:53 PM MDT
-- Model: New Model    Version: 1.0
-- MySQL Workbench Forward Engineering

SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0;
SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0;
SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='TRADITIONAL,ALLOW_INVALID_DATES';

-- -----------------------------------------------------
-- Schema patch_integrator
-- -----------------------------------------------------

-- -----------------------------------------------------
-- Schema patch_integrator
-- -----------------------------------------------------
CREATE SCHEMA IF NOT EXISTS `patch_integrator` DEFAULT CHARACTER SET utf8 ;
USE `patch_integrator` ;

-- -----------------------------------------------------
-- Table `patch_integrator`.`project`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `patch_integrator`.`project` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `url` VARCHAR(2000) NOT NULL,
  `name` VARCHAR(100) NULL,
  `is_done` TINYINT(1) NULL DEFAULT 0,
  PRIMARY KEY (`id`))
ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `patch_integrator`.`merge_commit`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `patch_integrator`.`merge_commit` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `commit_hash` CHAR(40) NOT NULL,
  `is_conflicting` TINYINT(1) NOT NULL,
  `parent_1` CHAR(40) NOT NULL,
  `parent_2` CHAR(40) NOT NULL,
  `project_id` INT NOT NULL,
  `is_done` TINYINT(1) NULL DEFAULT 0,
  `author_name` VARCHAR(150) NULL,
  `author_email` VARCHAR(150) NULL,
  `timestamp` INT NULL,
  PRIMARY KEY (`id`, `project_id`),
  UNIQUE INDEX `commit_hash_UNIQUE` (`commit_hash` ASC),
  INDEX `fk_merge_commit_project_idx` (`project_id` ASC),
  CONSTRAINT `fk_merge_commit_project`
    FOREIGN KEY (`project_id`)
    REFERENCES `patch_integrator`.`project` (`id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE)
ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `patch_integrator`.`refactoring_commit`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `patch_integrator`.`refactoring_commit` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `commit_hash` CHAR(40) NOT NULL,
  `is_done` TINYINT(1) NULL DEFAULT 0,
  `project_id` INT NOT NULL,
  PRIMARY KEY (`id`, `project_id`, `commit_hash`),
  INDEX `fk_refactoring_commit_project1_idx` (`project_id` ASC),
  CONSTRAINT `fk_refactoring_commit_project1`
    FOREIGN KEY (`project_id`)
    REFERENCES `patch_integrator`.`project` (`id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE)
ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `patch_integrator`.`refactoring`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `patch_integrator`.`refactoring` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `refactoring_type` VARCHAR(100) NULL,
  `refactoring_detail` VARCHAR(2000) NULL,
  `refactoring_commit_id` INT NOT NULL,
  `commit_hash` CHAR(40) NOT NULL,
  `project_id` INT NOT NULL,
  PRIMARY KEY (`id`, `refactoring_commit_id`, `commit_hash`, `project_id`),
  INDEX `fk_refactoring_refactoring_commit1_idx` (`refactoring_commit_id` ASC, `project_id` ASC, `commit_hash` ASC),
  CONSTRAINT `fk_refactoring_refactoring_commit1`
    FOREIGN KEY (`refactoring_commit_id` , `project_id` , `commit_hash`)
    REFERENCES `patch_integrator`.`refactoring_commit` (`id` , `project_id` , `commit_hash`)
    ON DELETE CASCADE
    ON UPDATE CASCADE)
ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `patch_integrator`.`conflicting_java_file`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `patch_integrator`.`conflicting_java_file` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `path` VARCHAR(1000) NOT NULL,
  `type` VARCHAR(45) NOT NULL,
  `merge_commit_id` INT NOT NULL,
  `project_id` INT NOT NULL,
  PRIMARY KEY (`id`, `merge_commit_id`, `project_id`),
  INDEX `fk_conflicting_java_file_merge_commit1_idx` (`merge_commit_id` ASC, `project_id` ASC),
  CONSTRAINT `fk_conflicting_java_file_merge_commit1`
    FOREIGN KEY (`merge_commit_id` , `project_id`)
    REFERENCES `patch_integrator`.`merge_commit` (`id` , `project_id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE)
ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `patch_integrator`.`conflicting_region`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `patch_integrator`.`conflicting_region` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `parent_1_start_line` INT NOT NULL,
  `parent_1_length` INT NOT NULL,
  `parent_1_path` VARCHAR(1000) NOT NULL,
  `parent_2_start_line` INT NOT NULL,
  `parent_2_length` INT NOT NULL,
  `parent_2_path` VARCHAR(1000) NOT NULL,
  `conflicting_java_file_id` INT NOT NULL,
  `merge_commit_id` INT NOT NULL,
  `project_id` INT NOT NULL,
  PRIMARY KEY (`id`, `conflicting_java_file_id`, `merge_commit_id`, `project_id`),
  INDEX `fk_conflicting_region_conflicting_java_file1_idx` (`conflicting_java_file_id` ASC, `merge_commit_id` ASC, `project_id` ASC),
  CONSTRAINT `fk_conflicting_region_conflicting_java_file1`
    FOREIGN KEY (`conflicting_java_file_id` , `merge_commit_id` , `project_id`)
    REFERENCES `patch_integrator`.`conflicting_java_file` (`id` , `merge_commit_id` , `project_id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE)
ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `patch_integrator`.`refactoring_region`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `patch_integrator`.`refactoring_region` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `start_line` INT NOT NULL,
  `length` INT NOT NULL,
  `type` CHAR(1) NOT NULL,
  `path` VARCHAR(1000) NOT NULL,
  `refactoring_id` INT NOT NULL,
  `refactoring_commit_id` INT NOT NULL,
  `commit_hash` CHAR(40) NOT NULL,
  `project_id` INT NOT NULL,
  PRIMARY KEY (`id`, `refactoring_id`, `refactoring_commit_id`, `commit_hash`, `project_id`),
  INDEX `fk_refactoring_region_refactoring1_idx` (`refactoring_id` ASC, `refactoring_commit_id` ASC, `commit_hash` ASC, `project_id` ASC),
  CONSTRAINT `fk_refactoring_region_refactoring1`
    FOREIGN KEY (`refactoring_id` , `refactoring_commit_id` , `commit_hash` , `project_id`)
    REFERENCES `patch_integrator`.`refactoring` (`id` , `refactoring_commit_id` , `commit_hash` , `project_id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE)
ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `patch_integrator`.`conflicting_region_history`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `patch_integrator`.`conflicting_region_history` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `commit_hash` CHAR(40) NOT NULL,
  `merge_parent` INT NOT NULL,
  `old_start_line` INT NOT NULL,
  `old_length` INT NOT NULL,
  `old_path` VARCHAR(1000) NULL,
  `new_start_line` INT NOT NULL,
  `new_length` INT NOT NULL,
  `new_path` VARCHAR(1000) NULL,
  `conflicting_region_id` INT NOT NULL,
  `conflicting_java_file_id` INT NOT NULL,
  `merge_commit_id` INT NOT NULL,
  `project_id` INT NOT NULL,
  `author_name` VARCHAR(150) NULL,
  `author_email` VARCHAR(150) NULL,
  `timestamp` INT NULL,
  PRIMARY KEY (`id`, `conflicting_region_id`, `conflicting_java_file_id`, `merge_commit_id`, `project_id`),
  INDEX `fk_conflicting_region_history_conflicting_region1_idx` (`conflicting_region_id` ASC, `conflicting_java_file_id` ASC, `merge_commit_id` ASC, `project_id` ASC),
  CONSTRAINT `fk_conflicting_region_history_conflicting_region1`
    FOREIGN KEY (`conflicting_region_id` , `conflicting_java_file_id` , `merge_commit_id` , `project_id`)
    REFERENCES `patch_integrator`.`conflicting_region` (`id` , `conflicting_java_file_id` , `merge_commit_id` , `project_id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE)
ENGINE = InnoDB;


SET SQL_MODE=@OLD_SQL_MODE;
SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS;
SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS;
