# MockLocation
This is a sample application that performs background impersonation of the current location information on Android using the service.

## Start mock location
```kotlin
MockLocationService.start(applicationContext, 40.7127, -74.0059)
```

## Stop mock location
```kotlin
MockLocationService.stop(applicationContext)
```
