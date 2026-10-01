// SPDX-FileCopyrightText: 2025 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

module.exports = {
  // `main` publishes stable versions; `next` publishes `-rc.N` prereleases on the
  // `next` distribution channel. Both are protected release branches, and a push to
  // either one may publish automatically.
  branches: [
    { name: 'main', channel: false },
    { name: 'next', prerelease: 'rc', channel: 'next' },
  ],
  tagFormat: '${version}',
  plugins: [
    ['@semantic-release/commit-analyzer', { preset: 'conventionalcommits' }],
    'semantic-release-export-data',
    ['@semantic-release/release-notes-generator', { preset: 'conventionalcommits' }],
    '@semantic-release/github',
  ],
};
