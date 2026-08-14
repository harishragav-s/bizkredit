# BizKredit - SME Loan Management System
 
## Overview
 
BizKredit is a full-stack SME (Small and Medium Enterprise) Loan Management System designed to streamline the complete loan lifecycle, from customer onboarding to loan approval and portfolio monitoring.
 
The application provides secure role-based access, enabling different stakeholders such as Applicants, Relationship Managers, Credit Analysts, Credit Committee Members, and Administrators to perform their respective responsibilities efficiently.
 
---
 
## Features
 
- JWT-based Authentication & Authorization
- Role-Based Access Control (RBAC)
- SME Customer Onboarding
- Loan Application Management
- KYC Verification
- Loan Product Management
- Credit Assessment & Scorecard
- Maker-Checker Workflow
- Portfolio Management
- Audit Trail
- Dashboard for Different User Roles
 
---
 
## Microservices Architecture
 
The backend of BizKredit has been upgraded to a distributed microservices architecture to ensure scalability and separation of concerns. The application is divided into the following individual services:
 
- **API Gateway (`api-gateway`)**: Handles routing and acts as the single entry point into the system.
- **Authentication Service (`auth-service`)**: Manages user credentials, registration, and role-based access scopes.
- **Collateral Service (`collateral-service`)**: Manages collateral records, facility accounts, and drawdowns.
- **Credit Service (`credit-service`)**: Handles financial analysis, credit proposals, and scorecard modeling.
- **Eureka Server (`eureka-server`)**: Service registry for microservice discovery.
- **Monitoring Service (`monitoring-service`)**: Tracks covenants, non-performing assets (NPAs), and early warning signals.
- **SME Loan Service (`sme-loan-service`)**: Manages SME business profiles, group companies, and loan products.
 
---
 
## Tech Stack
 
### Frontend
 
- React 18
- Vite
- React Router DOM
- Axios
- Bootstrap 5
- JavaScript (ES6+)
 
### Backend
 
- Java 21
- Spring Boot 3
- Spring Cloud (Eureka, API Gateway)
- Spring Security
- Spring Data JPA
- Hibernate
- JWT Authentication
- Maven
 
### Database
 
- MySQL
 
---
 
## Project Structure
 
The repository is structured into two main directories for the frontend and the backend microservices:
 
```text
bizkredit-main/
│
├── bizkredit-frontend/
│   ├── src/
│   │   ├── components/
│   │   ├── context/
│   │   ├── pages/
│   │   ├── services/
│   │   ├── styles/
│   │   └── utils/
│   ├── package.json
│   └── vite.config.js
│
└── bizkredit-microservices/
    ├── api-gateway/
    ├── auth-service/
    ├── collateral-service/
    ├── credit-service/
    ├── eureka-server/
    ├── monitoring-service/
    └── sme-loan-service/
```
 
---
 
## Prerequisites
 
- Java 21
- Maven 3.9+
- Node.js 18+
- npm
- MySQL 8
 
---
 
## Backend Setup (Microservices)
 
### 1. Clone the repository
 
```bash
git clone <repository-url>
```
 
### 2. Navigate to the microservices directory
 
```bash
cd bizkredit-main/bizkredit-microservices
```
 
### 3. Configure MySQL Databases
 
Update the database configurations for each relevant service in their respective `application.properties` or `application.yml` files.
 
Configure:
 
- Database URL
- Username
- Password
 
### 4. Build the projects
 
Build each microservice individually.
 
Example:
 
```bash
cd eureka-server
mvn clean install
```
 
### 5. Run the application
 
Start the services in the following order:
 
1. Eureka Server
2. API Gateway
3. Authentication Service
4. SME Loan Service
5. Credit Service
6. Collateral Service
7. Monitoring Service
 
Run each service using:
 
```bash
mvn spring-boot:run
```
 
---
 
## Frontend Setup
 
### 1. Navigate to frontend
 
```bash
cd bizkredit-main/bizkredit-frontend
```
 
### 2. Install dependencies
 
```bash
npm install
```
 
### 3. Start the development server
 
```bash
npm run dev
```
 
The frontend will start on:
 
```text
http://localhost:5173
```
 
---
 
## User Roles
 
- SME Applicant
- Relationship Manager
- Credit Analyst
- Credit Committee Member
- Administrator
 
---
 
## Authentication
 
- JWT Authentication
- Spring Security
- Role-Based Authorization
- Protected Routes (Frontend)
- API Gateway Token Validation (Backend)
 
---
 
## Application Flow
 
```text
Login (Auth Service)
        ↓
JWT Authentication via API Gateway
        ↓
Role Validation
        ↓
Dashboard
        ↓
Loan Management (SME Loan Service)
        ↓
Approval Workflow (Credit & Collateral Services)
        ↓
Portfolio Monitoring (Monitoring Service)
```
