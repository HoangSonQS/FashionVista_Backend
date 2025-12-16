# Context
Filename: phase2-wishlist-polish.md
Created: 2025-12-05 16:45
Author: AI
Protocol: RIPER-5 + Multi-Dim + Agent + AI-Dev Guide

# Task Description
Polish wishlist UX: badge/loading sync, handling when not logged in.

# Project Overview
FashionVista (React/TS + Spring Boot). Wishlist toggle already exists; need better UX cues.

---
# Analysis (Research)
TBD

# Proposed Solutions (Innovation)
## Plan A:
- Show loading on wishlist icon/button; badge count synced with server; prompt login gracefully.
## Plan B:
- Queue optimistic updates with rollback on error.

## Recommended Plan
Plan A (lighter, quick UX win).

# Implementation Plan (Planning)
Implementation Checklist:
1. Frontend: add global wishlist badge count (SiteHeader) synced on toggle/load.
2. Frontend: handle unauthenticated click => show login modal (already exists, ensure for wishlist button).
3. Frontend: loading state on toggle buttons (ProductDetail, ProductCard if any).
4. Frontend: Profile wishlist tab shows loading/empty states (already partial).

# Current Step
Executing: "Research components and data flow for wishlist badge/toggle."

# Task Progress

# Final Review
TBD

