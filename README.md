# PlantPal - Plant Care Management App

A comprehensive Android application for managing plant care across multiple environments with real-time monitoring capabilities.

## Features
- User Authentication
- Plant Care Management with Image Capture
- Shared Environment System with Role-based Access Control
- Event Calendar Integration
- Real-time Plant Monitoring
- Multiple Environment Management

## Tech Stack
- Kotlin
- Firebase
  - Authentication
  - Firestore
  - Storage
- Android Jetpack
  - Navigation Component
  - ViewBinding
- CameraX API
- Material Design Components
- MVVM Architecture
- Coroutines for Asynchronous Operations

## Architecture
- MVVM (Model-View-ViewModel) Architecture
- Repository Pattern
- Clean Architecture Principles

## Prerequisites
1. Android Studio
2. Firebase Account
3. Google Play Services

## Setup Instructions

### Firebase Setup
1. Create a new Firebase project at [Firebase Console](https://console.firebase.google.com/)
2. Add an Android app to your Firebase project:
   - Register app with package name
   - Download `google-services.json`
   - Place `google-services.json` in the app module directory
3. Enable Firebase Services:
   - Authentication (Email/Password)
   - Cloud Firestore
   - Storage

### Project Setup
1. Clone the repository
2. Open project in Android Studio
3. Sync project with Gradle files
4. Configure Firebase:
   - Add your `google-services.json`
   - Enable necessary Firebase services
5. Build and run the project

## Project Structure
```
app/
├── data/           # Data layer: repositories, data sources
├── di/             # Dependency injection
├── domain/         # Domain layer: use cases, models
├── presentation/   # UI layer: activities, fragments, viewmodels
├── utils/          # Utility classes and extensions
└── application/    # Application class
```

## Key Components
- **Authentication**: Firebase Authentication for user management
- **Database**: Firestore for storing plant and environment data
- **Storage**: Firebase Storage for image storage
- **Camera**: CameraX API for plant photo capture
- **Calendar**: Integration with device calendar for care reminders
- **Navigation**: Jetpack Navigation Component for screen navigation

## Features Implementation
1. **User Authentication**
   - Email/Password signup and login
   - User profile management
   - Role-based access control

2. **Plant Management**
   - Add/Edit/Delete plants
   - Image capture and storage
   - Care instructions and scheduling
   - Real-time monitoring

3. **Environment System**
   - Create/Manage multiple environments
   - Share environments with other users
   - Role-based permissions
   - Real-time updates

4. **Calendar Integration**
   - Schedule care events
   - Reminders and notifications
   - Event synchronization

## Important Notes
- Requires Android 5.0 (API level 21) or higher
- Internet connection required for real-time features
- Camera permission required for image capture
- Calendar permission required for event integration

## Future Enhancements
- Plant disease detection using ML
- Weather integration
- Community features
- Plant care statistics and analytics

Would you like me to add or modify any sections of this README?
