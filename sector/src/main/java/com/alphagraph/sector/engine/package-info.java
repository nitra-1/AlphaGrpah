/**
 * The Sector Engine: {@link com.alphagraph.sector.engine.SectorEngine} implements
 * {@code common.engine.Engine<SectorEngineInput, SectorScore>}, the first engine that aggregates
 * across many instruments into one score rather than scoring a single instrument
 * (Technical/Fundamental/Institutional, Modules 1.5-1.7, were all one-instrument-in,
 * one-score-out). Reads sector mapping directly via
 * {@link com.alphagraph.sector.mapping.SectorMappingReader} (reference is a shared kernel, not a
 * restricted peer domain) but needs {@code intelligence} to bridge market's price/volume history
 * for every constituent, same reason as Module 1.5 - see {@code intelligence.sector}.
 */
package com.alphagraph.sector.engine;
