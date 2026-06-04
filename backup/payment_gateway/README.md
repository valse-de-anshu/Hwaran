# Payment Gateway & Sanctuary Access Logic

This directory contains the source code for the "Sanctuary Access" (Premium/Payment) UI that was implemented in the Hwaran app.

## Overview

The "Sanctuary" was designed as a premium tier system that allows users to unlock specific features of the application. The UI follows a highly aesthetic, minimal, and "ethereal" design language consistent with the rest of the app.

## Component Breakdown

### 1. `ArchiveScreen.kt`
This is the main landing page for the payment gateway.
- **`ATMOSPHERES`**: A data list defining the available plans (Lifetime, Ads Removal, Themes).
- **Protocol Animation**: When a plan is "unlocked", it displays a unique fingerprint/biometric protocol animation using `AnimatedVisibility` and custom borders.
- **Scroll Logic**: Uses a `VerticalScroll` with an `EtherealBackground` that reacts to the scroll position to create a parallax-like depth effect.

### 2. `PaymentSheet.kt`
A modal-like overlay that appears when a user selects a plan.
- **Biometric Simulation**: Uses `rememberInfiniteTransition` to create a pulsing "SECURE LINK ESTABLISHED" indicator.
- **Payment Methods**: Custom `TenderOption` components for Google Pay and Credit Card selections.
- **Confirmation**: The `ConfirmButton` uses a shimmer effect (`LinearEasing` on an offset) and haptic feedback to provide a high-end feel.

### 3. `EtherealBackground.kt`
The visual engine for the archive section.
- **`MediaFragment`**: Represents floating UI elements (Manga eyes, Video frames, Vinyl records) that drift in the background.
- **Parallax Logic**: Each fragment has a `speed` multiplier applied to the `scrollValue`, causing elements to move at different rates relative to the foreground.
- **Dynamic Tinting**: The background glow color changes based on which plan the user is currently hovering over or selecting.

### 4. `EditorialPlate.kt`
The card-like component used to display each premium tier.
- **Layout**: Alternates between left and right alignment based on the `AtmosphereData.align` property.
- **Aesthetics**: Uses large, low-opacity background numbers and serif typography to create a magazine-style editorial look.

### 5. `MediaAssets.kt`
Contains the custom-drawn canvas graphics used in the background fragments.
- **`MangaEyePanel`**: Custom `Path` drawing of a stylized manga eye.
- **`AudioWaveform`**: Dynamic bar drawing using a loop and `drawRoundRect`.
- **`VinylRecord`**: concentric circles with a central gradient to simulate a physical record.

## Logic Flow
1. User enters via **Settings -> Lifetime Pass**.
2. Navigation via `AppNavGraph` to `ArchiveScreen`.
3. User scrolls through `EditorialPlate` items.
4. Selecting an item triggers `PaymentSheet`.
5. "Authorizing" (currently simulated) triggers the `unlocked` state in `ArchiveScreen`, showing the final success UI.
