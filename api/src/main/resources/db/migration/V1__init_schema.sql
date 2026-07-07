CREATE TYPE "Roles" AS ENUM ('ADMIN', 'USER');

CREATE TABLE "User" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "username" TEXT NOT NULL,
    "password" TEXT NOT NULL,
    "role" "Roles" NOT NULL DEFAULT 'USER',
    CONSTRAINT "User_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "Problem" (
    "id" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    CONSTRAINT "Problem_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "Submission" (
    "id" TEXT NOT NULL,
    "time" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "userId" TEXT NOT NULL,
    "problemId" TEXT NOT NULL,
    "verdict" TEXT NOT NULL,
    CONSTRAINT "Submission_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "Test" (
    "id" TEXT NOT NULL,
    "problemId" TEXT NOT NULL,
    "input" TEXT NOT NULL,
    "correctOutput" TEXT NOT NULL,
    CONSTRAINT "Test_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "User_username_key" ON "User"("username");

ALTER TABLE "Submission"
    ADD CONSTRAINT "Submission_userId_fkey"
    FOREIGN KEY ("userId") REFERENCES "User"("id")
    ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE "Submission"
    ADD CONSTRAINT "Submission_problemId_fkey"
    FOREIGN KEY ("problemId") REFERENCES "Problem"("id")
    ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE "Test"
    ADD CONSTRAINT "Test_problemId_fkey"
    FOREIGN KEY ("problemId") REFERENCES "Problem"("id")
    ON DELETE RESTRICT ON UPDATE CASCADE;
