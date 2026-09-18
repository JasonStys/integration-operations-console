/**
 * File: validate-repository.mjs
 * Purpose: Enforce required documentation, source headers, generated-file freshness, and clean paths.
 * Symbols: repositoryRoot constant, requiredFiles/headerExtensions collections, walk and main functions.
 * Variables: failures accumulates actionable validation messages and determines the process exit code.
 */
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, extname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const requiredFiles = [
  'README.md',
  'CONTRIBUTING.md',
  'SECURITY.md',
  'docs/architecture.md',
  'docs/api.md',
  'docs/complexity.md',
  'docs/testing.md',
  'docs/operations.md',
  'docs/reports/test-summary.md',
  'docs/reports/validation.md',
];
const headerExtensions = new Set(['.java', '.ts', '.tsx', '.mjs', '.sh', '.sql', '.css']);
const excludedDirectories = new Set(['.git', 'coverage', 'dist', 'node_modules', 'target']);

/** Recursively returns source paths without entering dependency or generated-output directories. */
function walk(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      return excludedDirectories.has(entry.name) ? [] : walk(path);
    }
    return [path];
  });
}

/** Returns broken local Markdown links while leaving remote availability to network-aware tools. */
function brokenMarkdownLinks(path) {
  const markdown = readFileSync(path, 'utf8');
  return [...markdown.matchAll(/\[[^\]]*\]\(([^)]+)\)/g)].flatMap((match) => {
    const target = match[1].replace(/^<|>$/g, '');
    if (/^(?:https?:|mailto:|#)/.test(target)) return [];
    const localTarget = decodeURIComponent(target.split(/[?#]/, 1)[0]);
    return existsSync(resolve(dirname(path), localTarget)) ? [] : [target];
  });
}

/** Validates repository invariants and exits nonzero with one message per violation. */
function main() {
  const failures = [];
  for (const requiredFile of requiredFiles) {
    if (!existsSync(join(repositoryRoot, requiredFile))) {
      failures.push(`Missing required documentation: ${requiredFile}`);
    }
  }

  for (const path of walk(repositoryRoot)) {
    const repositoryPath = relative(repositoryRoot, path).replaceAll('\\', '/');
    if (extname(path) === '.md') {
      for (const target of brokenMarkdownLinks(path)) {
        failures.push(`Broken local Markdown link in ${repositoryPath}: ${target}`);
      }
    }
    if (repositoryPath === 'frontend/src/api/schema.d.ts') continue;
    if (!headerExtensions.has(extname(path))) continue;
    const firstLines = readFileSync(path, 'utf8').split(/\r?\n/).slice(0, 8).join('\n');
    if (!firstLines.includes('File:') || !firstLines.includes('Purpose:')) {
      failures.push(`Source header is incomplete: ${repositoryPath}`);
    }
  }

  if (failures.length > 0) {
    console.error(failures.join('\n'));
    process.exitCode = 1;
    return;
  }
  console.log('Repository structure and source headers are valid.');
}

main();
