# Fix: Cannot find IdentityClient class

## Quick Fix

The issue is that Identity module needs to be built **before** Admin and Gateway modules. Maven reactor should handle this automatically, but if you're getting compilation errors, try:

### Option 1: Clean Build (Recommended)

```bash
# From project root (/Users/emma/Irish-Project/worspace/Shortlink-Backend)
mvn clean install -DskipTests
```

This will:
1. Build modules in correct order (Identity before Admin/Gateway)
2. Install Identity module to local Maven repository
3. Then build Admin and Gateway with Identity dependency available

### Option 2: Build Identity First, Then Rest

```bash
# Step 1: Build Identity and its dependencies
mvn clean install -pl identity -am -DskipTests

# Step 2: Build the rest
mvn clean package -Pdocker-build -DskipTests -DskipITs
```

### Option 3: Verify Module Order

Check `pom.xml` module order (should be):
```xml
<modules>
    <module>base</module>
    <module>persistence</module>
    <module>redis</module>
    <module>identity</module>      <!-- Must be before admin/gateway -->
    <module>admin</module>
    <module>shortlink</module>
    <module>gateway</module>
    ...
</modules>
```

## Verification

After building, verify Identity JAR exists:
```bash
ls -la identity/target/shortlink-identity-1.0-SNAPSHOT.jar
```

Check that Identity classes are in the JAR:
```bash
jar -tf identity/target/shortlink-identity-1.0-SNAPSHOT.jar | grep IdentityClient
```

Should show:
```
org/tus/shortlink/identity/client/IdentityClient.class
org/tus/shortlink/identity/client/impl/IdentityClientImpl.class
```
