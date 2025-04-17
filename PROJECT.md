# SimpleLogUpload Project Documentation

## Overview

SimpleLogUpload is an Android application designed to upload log files to a server. It provides both standard and chunked upload capabilities to handle files of various sizes across different network conditions.

## Documentation Structure

This project follows specific documentation conventions:

1. **Main documentation**:
   - `README.md` - General project information and setup instructions
   - `PROJECT.md` (this file) - Project structure and documentation guidelines

2. **Feature documentation**:
   - Each feature must have its own dedicated Markdown file in the `./docs` folder
   - Feature documentation should thoroughly explain the implementation, usage, and any special considerations

3. **Task tracking**:
   - `TASK.md` - Contains ongoing tasks and development roadmap

## Documentation Requirements

### Feature Documentation Rule

**IMPORTANT**: For each new feature added to the project, a corresponding Markdown file must be created in the `./docs` folder. This file should:

1. Have a clear, descriptive filename (e.g., `FEATURE_NAME.md`)
2. Contain comprehensive documentation including:
   - Feature overview and purpose
   - Technical implementation details
   - API descriptions (if applicable)
   - Usage examples
   - Configuration options
   - Testing considerations

### Current Feature Documentation

The current feature documentation includes:

- `docs/CHUNKED_UPLOAD.md` - Details on the chunked upload implementation
- `docs/LARGE_UPLOAD.md` - Analysis and improvements for large file uploads
- `docs/RELEASE_PROCESS.md` - Guidelines for the release process

## Maintaining Documentation

When modifying or extending existing features, ensure the corresponding documentation is updated to reflect the changes. Keep all documentation accurate and up-to-date with the current implementation.

## Adding New Documentation

To add documentation for a new feature:

1. Create a new Markdown file in the `./docs` folder
2. Follow the naming convention `FEATURE_NAME.md`
3. Use clear sections and subsections for easy navigation
4. Include code examples where appropriate
5. Add links to the new documentation in other relevant documents

## Documentation Standards

All documentation should:
- Be clear and concise
- Include code examples where applicable
- Explain the "why" as well as the "how"
- Be kept up-to-date with code changes
- Follow a consistent format and style
