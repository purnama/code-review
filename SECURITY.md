# Security Policy

## Supported Versions

Currently, security updates are only available for the latest version of the application. If you're running an older version, please update to the latest release.

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

## Reporting a Vulnerability

We take the security of our code review application seriously. If you believe you've found a security vulnerability, please follow these steps:

1. **Do not disclose the vulnerability publicly** until we've had a chance to address it.
2. Send details of the vulnerability to arthur@purnama.de.
3. Include the following in your report:
   - A description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Any suggestions for remediation
   
## What to Expect
   
- We'll acknowledge receipt of your vulnerability report within 48 hours.
- We'll provide a timeframe for addressing the vulnerability after assessing it.
- We'll keep you informed about our progress in resolving the issue.
- Once the vulnerability is fixed, we'll credit you for the discovery (unless you request anonymity).

## Security Best Practices

When deploying this application, please follow these security best practices:

1. **Sensitive Credentials**: Never commit API keys, tokens, or passwords to the repository. Use environment variables or secure secret management.
2. **Regular Updates**: Keep the application and all dependencies up to date with the latest security patches.
3. **Secure Configuration**: Follow the guidelines in PRODUCTION.md for secure deployment.
4. **Access Control**: Limit access to the application to authorized users only.
5. **Database Security**: Ensure your database is properly secured and not directly exposed to the internet.
6. **Network Security**: Use firewalls and VPNs to restrict access to the application and its components.
