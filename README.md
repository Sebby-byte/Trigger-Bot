# PvP Test Clicker (Fabric, Minecraft 26.3, client-side)

A developer tool for testing PvP mechanics (hit detection, reach, knockback, custom weapons)
without clicking by hand. While on, it simulates a press of your Attack key (a plain mouse click)
whenever another player is within the detection range. It sends no attack commands of its own:
the game handles the click as if you pressed the button. You still aim; it never moves your camera.

## Keys (rebindable in Controls)
- J: toggle on/off (starts OFF each launch)
- . and ,: detection range +/- 0.5 blocks (0.5 to 16, default 4)

## Where it works
- Singleplayer: always.
- Multiplayer: only servers listed in `allowedServers` in `config/pvptestclicker.properties`
  (default: localhost, 127.0.0.1). Anywhere else it refuses to turn on.

## Config (`.minecraft/config/pvptestclicker.properties`)
- detectionRange: blocks (also changed with the keys)
- requireCrosshairOnPlayer: true (default) only clicks while your crosshair is on another player within range; false clicks whenever any player is within range, wherever you're aiming
- requireCooldown: true waits for the attack cooldown before each click; false clicks every tick
- clickWhenAimingAtBlocks: false (default) skips the click while your crosshair is on a block, so it can't mine or break builds
- allowedServers: comma-separated addresses, e.g. localhost,127.0.0.1,my-dev-server.local

Note: the click is a left mouse click, so it assumes Attack is on the default left mouse button.

Only use this where automated clicking is explicitly permitted. Most public servers prohibit it.

Needs: Minecraft 26.3, Fabric Loader 0.19.5+, Fabric API 0.161.0+26.3, Java 25.
