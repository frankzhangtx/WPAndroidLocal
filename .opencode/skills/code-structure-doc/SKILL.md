---
name: code-structure-doc
description: Use when the user asks to analyze code structure, code flow, architecture, or process and save/document it. Triggers on keywords like "分析代码结构", "分析流程", "代码结构文档", "保存流程图", "generate code structure document", "architecture analysis". Generates HTML documentation with SVG flowcharts and saves to the 代码结构文档 folder.
---

# Code Structure Documentation Skill

Generate comprehensive, visually rich HTML documentation for code structures and flows, saving them to the project's `代码结构文档/` folder.

## When to Use

Use this skill when the user asks to:
- Analyze code structure or architecture and save the result
- Analyze a code flow or process and generate documentation
- Create a flowchart or diagram for code logic
- Document code structure in HTML format
- Keywords: "分析代码结构", "分析流程", "保存", "代码结构文档", "流程图"

## Output Location

All generated HTML files MUST be saved to:

```
<project-root>/代码结构文档/
```

Create the folder if it does not exist. Name files descriptively in kebab-case, e.g.:
- `login-flow-analysis.html`
- `data-sync-architecture.html`
- `notification-flow.html`

## Analysis Process

Follow these steps in order:

### Step 1: Explore and Understand

1. Use `glob` and `grep` to locate all relevant source files
2. Use `Task` (explore agent) for thorough codebase exploration when the scope is large
3. Identify: entry points, core classes, data flow, state management, error handling
4. Trace the complete flow from start to end

### Step 2: Analyze Architecture

Identify and document:
- **Entry points**: How the flow begins (user action, deep link, system event, etc.)
- **Core classes**: Each class's name, location, and single-sentence responsibility
- **Data flow**: How data moves between layers (UI → ViewModel → Repository → Network/DB)
- **State management**: Where and how state is persisted (SharedPreferences, DB, in-memory)
- **Navigation/routing**: How screens connect and how decisions branch
- **Error handling**: How failures are detected and surfaced to the user
- **Framework specifics**: DI modules, FluxC actions/stores, etc.

### Step 3: Generate HTML Documentation

Produce a single self-contained HTML file with the following structure:

#### Required HTML Structure

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>[Topic] — 代码结构分析</title>
    <style>
        /* Use the CSS design system below */
    </style>
</head>
<body>
<div class="container">
    <h1>[Title]</h1>
    <p class="subtitle">[One-line summary]</p>
    <div class="toc">[Table of contents]</div>

    <!-- Section 1: Architecture Overview -->
    <h2 id="overview">1. 架构概览</h2>
    <div class="card">[Architecture description with layer diagram]</div>

    <!-- Section 2+: Each major flow gets its own section with SVG flowchart -->
    <h2 id="flow-X">N. [Flow Name]</h2>
    <div class="flow-diagram">
        <svg><!-- SVG flowchart --></svg>
    </div>
    <div class="card">
        <h3>详细步骤说明</h3>
        <ol>[Step-by-step explanation]</ol>
    </div>

    <!-- Comparison / Differences section if applicable -->
    <!-- State Management section -->
    <!-- Core Classes table -->
    <!-- Footer with generation date -->
</div>
</body>
</html>
```

#### Required CSS Design System

Use these CSS variables and styles to maintain visual consistency across all generated documents:

```css
:root {
    --wp-blue: #0073aa;
    --jp-green: #069e3f;
    --bg: #f9fafb;
    --card-bg: #ffffff;
    --text: #1e293b;
    --text-secondary: #64748b;
    --border: #e2e8f0;
}
```

- Cards: `background: var(--card-bg); border: 1px solid var(--border); border-radius: 12px; padding: 1.5rem;`
- Flow diagrams: same card style, with `overflow-x: auto;` for wide SVGs
- Info notes: `background: #eff6ff; border-left: 4px solid var(--wp-blue); padding: 0.75rem 1rem; border-radius: 0 8px 8px 0;`
- Warning notes: `background: #fef3c7; border-left: 4px solid #f59e0b;`
- Error notes: `background: #fef2f2; border-left: 4px solid #ef4444;`

#### SVG Flowchart Guidelines

- Use `viewBox` for responsiveness (e.g., `viewBox="0 0 900 820"`)
- Node types and colors:
  - **Start/End**: Rounded pill (`rx="22"`), solid fill (blue for WP.com, green for self-hosted, purple for magic link)
  - **Process**: Rounded rect (`rx="8"`), white fill with border or light blue/green tinted fill
  - **Decision**: Diamond polygon, yellow/amber fill (`#fef3c7`)
  - **External action** (Custom Tabs, browser): Yellow tinted card (`#fef3c7` + `#fcd34d` border)
  - **Success outcome**: Green tinted (`#f0fdf4` + `#86efac` border)
  - **Error outcome**: Red tinted (`#fef2f2` + `#fca5a5` border)
- Arrows: `<line>` with `marker-end="url(#arrow)"`, use dashed lines for navigation jumps
- Text: 13px font, bold for titles, 11px for secondary details
- Keep diagrams vertical (top-to-bottom flow), max width 900px in viewBox

#### Required Sections per Flow

Each flow MUST include:
1. **SVG flowchart** in a `.flow-diagram` container
2. **Ordered step-by-step explanation** in a `.card` after the diagram
3. **Class/method references** with file paths where relevant

#### Required Common Sections

Every document MUST include:
1. **目录 (Table of Contents)** with anchor links
2. **架构概览** — high-level architecture with layer description
3. **核心类职责说明** — table with columns: 类名 | 所在模块 | 职责
4. **Footer** — generation date note: `<div class="note">文档生成时间：YYYY-MM-DD | 基于项目源码静态分析自动生成</div>`

### Step 4: Save and Confirm

1. Ensure `代码结构文档/` folder exists: `mkdir -p <project-root>/代码结构文档/`
2. Write the HTML file to `<project-root>/代码结构文档/<descriptive-name>.html`
3. Confirm the file path to the user

## Quality Checklist

Before saving, verify:
- [ ] All SVG flowcharts render correctly (valid viewBox, proper arrow markers)
- [ ] Every node in the flowchart has a corresponding explanation in the text
- [ ] Class names and file paths are accurate (cross-reference with actual source)
- [ ] Table of contents links match section IDs
- [ ] CSS is self-contained (no external dependencies)
- [ ] Document is fully in Chinese (zh-CN) unless the user requests English
- [ ] Footer includes generation date
