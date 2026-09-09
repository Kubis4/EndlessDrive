# Endless Drive

Endless Drive is a 2.5D mobile survival-driving game about keeping an old car
alive on a road that never seems to end.

You begin beside a badly damaged vehicle with missing parts, uncertain fluids
and very little to rely on. The road ahead is procedurally generated, the next
building is never guaranteed to be useful, and every kilometre puts more wear
on the car. The goal is simple: prepare, drive, explore, repair and make it
farther than before.

## The game

Endless Drive is built around the car as the main character. Every part has a
purpose and every decision affects the journey:

- find a way to start the damaged car,
- inspect and repair individual components,
- manage fuel, oil, coolant and battery charge,
- search abandoned buildings for parts, fluids and useful supplies,
- choose between safer roads and risky shortcuts,
- adapt to hills, rough surfaces, snow, ice, mud, water and sand,
- keep enough space and carrying capacity for the next valuable find.

There is no traditional mission marker telling you exactly what to do. The
journey itself creates the pressure. A weak alternator, a punctured tyre, bad
fuel or an empty tank can turn a normal stop into a serious problem.

## Core gameplay loop

1. Start with a damaged, partially stripped sedan.
2. Search the nearby shed and first buildings for what the car needs.
3. Install parts, mix or replace fluids and get the engine running.
4. Drive until the next useful stopping point.
5. Stop, explore and loot buildings without losing valuable cargo.
6. Repair, upgrade and reorganise the car and inventory.
7. Reach the next depot, relay station or distance milestone.
8. Continue the run, or begin again with more experience and a better record.

## A car worth looking after

The sedan is assembled from individual components rather than one simple
health bar. The engine, battery, alternator, starter, radiator, suspension,
drivetrain, brakes, tyres, fuel tank, body panels, lights and cargo equipment
can all matter during a run.

Parts can be missing, worn, damaged or replaced with something better. A roof
rack and extra cargo storage let you carry more loot, but added weight and
aerodynamic drag make the car harder to drive efficiently.

Fuel, oil and coolant also have quality. Contaminated fluids may still keep the
car moving, but they reduce performance, increase consumption or make the
engine run dangerously hot. A careful driver learns when to save a good part,
when to install a temporary replacement and when to leave something behind.

## A road that keeps changing

The world is generated from a seed, so the route, terrain, buildings, loot and
events develop as the journey continues. The road can include:

- long climbs and descents,
- winding sections and broken pavement,
- bridges over deep ravines,
- asphalt, concrete, gravel, dirt and sand branches,
- water, mud, loose gravel, slush and ice on the road,
- abandoned homes, garages, service stations and repair locations.

Road choices are made while driving. A safer branch may cost time or fuel,
while a rough shortcut may lead to better loot and much greater risk.

## Weather, wind and time

The journey has a day and night cycle with changing skies, lighting and
visibility. Rain, snow, fog and storms affect the atmosphere as well as the
way the car handles.

Headwind slightly slows the car and increases fuel consumption. Tailwind gives
the car a small push and helps it travel farther. These effects are deliberately
subtle, but they matter when the tank is nearly empty or the road climbs for a
long time.

Winter arrives later in the journey. Snow tyres and chains become valuable,
while water in the cooling system can freeze and damage the engine. Chains help
on snow but reduce speed and grip on dry roads, so good preparation means
changing equipment at the right time.

## Depots and relay stations

Depots create a rhythm for a long run. They offer a reliable chance to refuel,
find stronger parts and prepare for the next difficulty increase. They are
safe milestones, not a finish line.

Relay stations are long-term route objectives. A relay must be discovered and
looted for its equipment before it can be repaired and activated. Once active,
it becomes a permanent achievement of that journey and provides a meaningful
reason to explore beyond the nearest buildings.

Starting a new run resets the current journey, including discovered and
activated relays. Permanent records, achievements and the best distance remain
available through the player profile and Google Play Games when the player is
signed in.

## Long-term goals

Endless Drive is designed for journeys well beyond the first 25 kilometres.
Difficulty grows gradually rather than stopping at a fixed map end:

- terrain becomes more demanding,
- useful loot becomes less predictable,
- parts wear down over longer distances,
- adverse events become more dangerous,
- fuel and repair decisions become increasingly important,
- winter equipment and better upgrades become necessary.

Milestones, relay stations, building exploration, vehicle upgrades, weather
events and personal distance records provide goals during the run. Achievements
include surviving difficult conditions, restoring the whole vehicle, managing
fuel and reaching extreme distances.

## Social features and rewards

Players can sign in with Google Play Games, choose a nickname and country, and
submit their best distance and time to the Endless Distance leaderboard. The
game also includes achievements for exploration, upgrades, relays, weather,
fuel management and exceptional runs.

Optional rewarded advertisements can provide limited emergency help, such as
recovering from a failed run or doubling collected scraps. Ads are never part
of the basic driving loop and are handled with Google AdMob and consent tools.

## Privacy

[Privacy Policy](https://github.com/Kubis4/EndlessDrive/blob/master/privacy-policy.md)

Direct text version:
https://raw.githubusercontent.com/Kubis4/EndlessDrive/master/privacy-policy.md

## Project

Endless Drive is an Android Studio project written in Kotlin with Jetpack
Compose. It uses a custom lightweight driving and world-rendering system
designed for mobile devices, procedural Canvas scenery and local DataStore
saves.

To open the project, use Android Studio with JDK 17 and Android SDK API 36.
The minimum supported Android version is API 24. The game is designed for
landscape play.
