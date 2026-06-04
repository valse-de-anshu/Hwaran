import re

with open("app/src/main/java/com/ballade/hwaran/ui/screens/NowPlayingScreen.kt", "r") as f:
    content = f.read()

shadow_modifier = "modifier = Modifier.shadow(8.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black).background(Color.Black.copy(alpha = 0.15f), CircleShape)"

# Add imports for shadow and CircleShape if missing
if "import androidx.compose.ui.draw.shadow" not in content:
    content = content.replace("import androidx.compose.ui.draw.drawWithContent", "import androidx.compose.ui.draw.drawWithContent\nimport androidx.compose.ui.draw.shadow\nimport androidx.compose.foundation.shape.CircleShape")

# We want to add the modifier to the IconButtons in NowPlayingScreen (Portrait and Landscape)
# Landscape:
# 1. Shuffle
content = content.replace(
    "IconButton(onClick = { \n                            musicViewModel.toggleShuffle()",
    f"IconButton(onClick = {{ \n                            musicViewModel.toggleShuffle()",
)
# Actually it is easier to replace specific lines:

replacements = [
    # Landscape Shuffle
    (
        "IconButton(onClick = { \n                            musicViewModel.toggleShuffle()",
        f"IconButton({shadow_modifier}, onClick = {{ \n                            musicViewModel.toggleShuffle()"
    ),
    # Landscape Previous
    (
        "IconButton(onClick = { musicViewModel.previous() }, enabled = hasPrevious)",
        f"IconButton(onClick = {{ musicViewModel.previous() }}, enabled = hasPrevious, {shadow_modifier})"
    ),
    # Landscape Next
    (
        "IconButton(onClick = { musicViewModel.next() }, enabled = hasNext)",
        f"IconButton(onClick = {{ musicViewModel.next() }}, enabled = hasNext, {shadow_modifier})"
    ),
    # Landscape Repeat
    (
        "IconButton(onClick = { showRepeatMenu = true })",
        f"IconButton(onClick = {{ showRepeatMenu = true }}, {shadow_modifier})"
    ),
    # Landscape Speaker
    (
        "IconButton(onClick = { \n                            val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)",
        f"IconButton({shadow_modifier}, onClick = {{ \n                            val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)"
    ),
    # Landscape Share
    (
        "IconButton(onClick = { \n                            currentChapter?.let { chapter ->",
        f"IconButton({shadow_modifier}, onClick = {{ \n                            currentChapter?.let {{ chapter ->"
    ),
    # Landscape Queue
    (
        "IconButton(onClick = { showQueueDialog = true })",
        f"IconButton(onClick = {{ showQueueDialog = true }}, {shadow_modifier})"
    ),
    # Portrait Shuffle
    (
        "IconButton(onClick = { \n                    musicViewModel.toggleShuffle()",
        f"IconButton({shadow_modifier}, onClick = {{ \n                    musicViewModel.toggleShuffle()"
    ),
    # Portrait Previous
    (
        "IconButton(\n                    onClick = { musicViewModel.previous() },\n                    enabled = hasPrevious\n                )",
        f"IconButton(\n                    onClick = {{ musicViewModel.previous() }},\n                    enabled = hasPrevious,\n                    {shadow_modifier}\n                )"
    ),
    # Portrait Next
    (
        "IconButton(\n                    onClick = { musicViewModel.next() },\n                    enabled = hasNext\n                )",
        f"IconButton(\n                    onClick = {{ musicViewModel.next() }},\n                    enabled = hasNext,\n                    {shadow_modifier}\n                )"
    ),
    # Portrait Repeat (already covered by Landscape Repeat? Let's check if they are identical)
    (
        "                    IconButton(onClick = { showRepeatMenu = true }) {",
        f"                    IconButton(onClick = {{ showRepeatMenu = true }}, {shadow_modifier}) {{"
    ),
    # Portrait Speaker
    (
        "IconButton(onClick = { \n                    val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)",
        f"IconButton({shadow_modifier}, onClick = {{ \n                    val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)"
    ),
    # Portrait Share
    (
        "IconButton(onClick = { \n                    currentChapter?.let { chapter ->",
        f"IconButton({shadow_modifier}, onClick = {{ \n                    currentChapter?.let {{ chapter ->"
    )
]

for old, new in replacements:
    content = content.replace(old, new)

with open("app/src/main/java/com/ballade/hwaran/ui/screens/NowPlayingScreen.kt", "w") as f:
    f.write(content)
