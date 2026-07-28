/**
 * Bridges market's published price history to the Technical Engine (Module 1.5). Per
 * docs/001_System_Architecture.md §4: domain modules never depend on each other directly (Rule
 * 3), so {@code technical} cannot read {@code market}'s tables itself; {@code intelligence} is
 * the module allowed to depend on both (Rule 4), read market's data via its published API, build
 * technical's input DTO, invoke the engine, and hand the result to technical's own writer.
 */
package com.alphagraph.intelligence.technical;
