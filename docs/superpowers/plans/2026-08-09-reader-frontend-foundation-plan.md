# Reader Frontend Foundation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` or `superpowers:subagent-driven-development` when implementing this plan.

**Goal:** Build the first frontend foundation for the reader product using `UniApp` for the C-end H5/mini-program/App outputs, and `plus-ui` only for the admin console, while staying aligned with the existing `ruoyi-reader` backend contracts.

**Fixed Repositories:**

- Backend: `/Users/yabin/code/ruoyi/RuoYi-Vue-Plus`
- Admin frontend: `/Users/yabin/code/ruoyi/plus-ui` on branch `6.X-Vue`
- C-end frontend: `/Users/yabin/code/ruoyi/reader-uniapp` as a dedicated `UniApp` project

## Scope

This plan covers only the frontend foundation:

1. `plus-ui` admin route scaffold for reader management
2. `UniApp` project bootstrap for H5/mini-program/App
3. shared C-end reader API client/types in `reader-uniapp`
4. admin-side reader API/types in `plus-ui`
5. cross-end contract alignment with `ruoyi-reader`

Out of scope:

- full visual polish
- payment/membership
- comments/community
- authorized source sync console

## Task 1: Build `plus-ui` admin API layer

**Files:**

- Create: `src/api/reader/admin/`

- [ ] Add admin-side API wrappers for work list, import task list, audit list, publish actions
- [ ] Add admin-side TypeScript models matching `ruoyi-reader` admin DTOs

## Task 2: Build `plus-ui` admin page scaffold

**Files:**

- Create: `src/router/modules/reader-admin.ts`
- Create: `src/views/reader-admin/`

- [ ] Add reader admin route module
- [ ] Add placeholder pages for work management, import tasks, audit, publish log
- [ ] Wire menu permissions to existing `plus-ui` admin permission flow
- [ ] Reuse existing table/form/upload components where possible

## Task 3: Create the `UniApp` C-end scaffold

**Files:**

- Create: `/Users/yabin/code/ruoyi/reader-uniapp`

- [ ] Initialize a minimal `UniApp` project
- [ ] Add H5 output structure
- [ ] Add four-tab structure: home, discover, bookshelf, profile
- [ ] Add pages for work detail, catalog, novel reader, comic reader
- [ ] Add shared request wrapper aligned with `reader app API`
- [ ] Reserve wrappers for WeChat login, share, and safe-area adaptation

## Task 4: Complete contract alignment and joint verification

- [ ] Confirm `reader app API` field names, pagination model, and auth semantics are identical for `UniApp H5` and mini-program
- [ ] Confirm guest mode works in H5 and mini-program browse flows
- [ ] Confirm bookshelf/progress endpoints support logged-in sync flows
- [ ] Run joint smoke flow: import -> publish -> H5 read -> bookshelf/progress -> mini-program continue reading

## Verification

- `plus-ui` can build with the new reader admin route and API scaffolds
- `UniApp` can render the minimal four-tab scaffold
- Backend contracts are documented and frozen before large UI work starts

## Current verification record

- `reader-uniapp` follows the official Vue3/Vite `UniApp` project structure.
- H5 development server starts on port `5175` and returns HTTP `200`.
- H5 production build passes with `pnpm build:h5`.
- WeChat mini-program production build passes with `pnpm build:mp-weixin`.
- The C-end project is isolated on branch `analysis/reader-miniapp-fit`.
