# Java Code Standards

## JavaDoc Standards

The Code Review project follows these standards for JavaDoc placement:

1. JavaDoc comments should be placed **after imports** and **before class declarations and annotations**.

Example:
```java
package de.purnama.code_review.config;

import org.springframework.stereotype.Service;
import java.util.List;

/**
 * MyClass
 * 
 * Description of what this class does
 * 
 * @author Arthur Purnama (arthur@purnama.de)
 */
@Service
public class MyClass {
    // Class implementation
}
```

2. Each class should include the author information in the format:
```java
@author Arthur Purnama (arthur@purnama.de)
```

3. Class JavaDoc should include the class name and description:
```java
/**
 * MyClass
 * 
 * Detailed description of what this class does.
 * Multiple lines are fine for longer descriptions.
 * 
 * @author Arthur Purnama (arthur@purnama.de)
 */
```

4. Method JavaDocs should follow standard conventions:
```java
/**
 * Brief description of what the method does
 *
 * @param param1 Description of first parameter
 * @param param2 Description of second parameter
 * @return Description of the return value
 * @throws ExceptionType When/why this exception is thrown
 */
public ReturnType methodName(ParamType param1, ParamType param2) throws ExceptionType {
    // Method implementation
}
```

## Authorship Information

All Java files in this project should include authorship information. The `add-author-info.sh` script can be used to add author information to any new files.

Run it like this:
```bash
chmod +x add-author-info.sh
./add-author-info.sh
```

## Automatic Formatting

If the JavaDoc comments are incorrectly positioned, you can run the `fix-javadoc-position.sh` script to automatically fix them:

```bash
chmod +x fix-javadoc-position.sh
./fix-javadoc-position.sh
```
