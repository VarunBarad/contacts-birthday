# Build the code-base
build:
  ./gradlew -x test build

# Build the code and run the tests
test:
  ./gradlew test

# Start local server
run:
  ./gradlew bootRun
