/**
 * Module 2.3: Corporate Event Engine. Classifies canonical document knowledge (topics/facts,
 * produced by {@link com.alphagraph.corporate.knowledge}) into zero or more of 13 named corporate
 * events - a deterministic rule engine, not an LLM caller. Retrofitted from the original design
 * (which called Claude directly on raw document text) once a second engine (Order Book, Module
 * 2.4) needed to read the same documents - one shared canonical extraction pass now feeds every
 * downstream rule engine, rather than each engine re-reading the PDF text and potentially
 * extracting inconsistent values for the same fact.
 */
package com.alphagraph.corporate.events;
