# Changelog

## [3.10.0](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/compare/3.9.0...3.10.0) (2024-02-27)


### 🦊 CI/CD

* **DHEI-14954:** improvements for docker-build and docs ([e41ea2f](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/e41ea2f4ac309015491183e268820ae95d79b14a))
* opensource condition moved to template ([1677f46](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/1677f4631e3c2b679b4e09a4a2735633c472dd49))
* **gitlab:** added opensource pipeline ([e7c7445](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/e7c744591194f30d3e633a98f942b8fde965ccd5))
* **reuse:** added .m2 to gitignore so reuse does not lint it ([11ac4d2](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/11ac4d212c25236e3629a510b4acf81f257a6774))


### 🚀 Features

* create pre-licensed empty .gitignore ([dc53246](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/dc5324666cf5a6ff8f9e91b63474d5998d44f4b1))


### Other

* added reuse-config; removed license-info from auto-generated changelog ([8dc95a4](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/8dc95a48bbd5fb136ba8493d95549f39473cf0e5))
* initial commit for github ([#1](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/issues/1)) ([2f0c495](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/2f0c495c4ec12f7fe793e1a1c02fffd10a2db646))
* initial commit for github ([#1](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/issues/1)) ([19ee3d8](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/19ee3d8d92f3d9134c4790d1f4139b5e84c782ce))
* sync from gitlab ([d38cc08](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/d38cc08b55afa4e1a9a415e5e427d31e18be0fbd))
* sync from gitlab ([65be9f4](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/65be9f4d0b998084df7c0f8fa2f4e6257e9da9e7))
* **release:** 3.9.0 ([b603bda](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/b603bdaf4b6b77c07094c7ff6b3c14a565ef70b3))

## [3.9.0](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/compare/3.8.0...3.9.0) (2023-11-23)


### 📔 Docs

* update README.md ([58277e3](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/58277e3e7bf7108eb80eb5ada612998655639be7))


### 🦊 CI/CD

* updated pipeline ref ([d91570a](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/d91570a5dd4fe2c7954825593cc1a4468f558022))


### 🚀 Features

* extend key structure with env ([b183188](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/b18318844aa4fadb64db8b664b7559b02670d0d8))


### 🛠 Fixes

* utilize already set values, avoid not needed operations ([ba05df2](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/ba05df2b588f61ccec631d4ce4a5d95ebba24232))


### Other

* **release:** 3.8.0 ([26fcaca](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/26fcacaeb4d6c3abdcff8aa17c8e2bc5dfffe591))

## [3.8.0](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/compare/3.7.0...3.8.0) (2023-10-23)


### :scissors: Refactor

* use lomboks @RequiredArgsConstructor within HttpClientConfiguration ([021ceb9](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/021ceb9573837cb247111693b425beb10679029e))
* WebClients for OauthTokenUtil and SpectreService are now defined as beans and injected matched by name via RequiredArgsConstructor ([9a9f7fe](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/9a9f7fe66761975643e8f7786816fce7067d9cd3))
* WebClients for OauthTokenUtil and SpectreService are now defined as... ([4ccbe0d](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/4ccbe0d5bebb0d10eecd53fe713947912555ec6a))


### 💈 Style

* apply spotless formatting :lipstick: ([fe0c8ca](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/fe0c8ca881c6063e976d58db298985e2cf1ff689))
* apply spotless formatting :lipstick: ([5a8617f](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/5a8617fc8c59b20893d1522e333741473fda0d72))


### 🚀 Features

* enable ProxySupport also for external IDPs within jumper ([5a3f91a](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/5a3f91a0bf1582efff639733fdaed820a544e146))
* enable ProxySupport within Jumper ([66f7b75](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/66f7b75b74fd6a5901399e74ee5d31dbf0aa7074))


### 🛠 Fixes

* same customized SSL Context is now used in oauthTokenUtilWebClient ([ecf5ba1](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/ecf5ba1b41b0669b13437c6512244e438aec9bb6))
* scenario Consumer calls proxy route with jc with oauth, oauth wrong credential headers set ([f7d314c](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/f7d314cd23ccf4cee25f8995574c8f24c3337005))
* use cause for filtering SslHandshakeTimeoutException ([ff484b3](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/ff484b33909ca38cb9592f7a660e9e3afb617a26))


### Other

* **release:** 3.7.0 ([3a34e8c](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/3a34e8c8e4b48e1b95510ab28b1507f89f9c20f7))

## [3.7.0](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/compare/3.6.4...3.7.0) (2023-10-17)


### 🚀 Features

* **java17:** upgraded project to java17 and maven 3.8.3 in the image ([8ca66e3](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/8ca66e349e4694f1cf4911ca6ec1df2236c97b34))


### Other

* **java17fix:** fix version ([7ec654d](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/7ec654d44272f06715367e4a2e37dd3ff638f906))
* **release:** 3.6.4 ([15d1564](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/15d156400e17dfc1a4e782d074c11779f1dd300d))
* **release:** release 3.6.5 ([9828984](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/982898415f1178cfab1bbd8177cae20ac8699610))

## [3.6.4](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/compare/3.6.3...3.6.4) (2023-10-09)


### 💈 Style

* add .gitattributes for line endings ([c17deb0](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/c17deb006a7ed7ac0fa1bdb5bd5ef6d4b96e5b18))
* fix formatting ([c5dd02d](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/c5dd02d22a4696ce939bc72262d0ebb88b2b039a))
* fix line endings ([ece51fe](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/ece51fe0672b9f35843e05ea9ac55a6664d02dce))


### 🛠 Fixes

* **ci:** push to harbor ([8f0c020](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/8f0c0202f1936e18193367b4eee971c6b7e7b679))
* **ci:** variables must be string ([9bbcb95](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/9bbcb95fe9060b087d10c8bc04db470ad002510e))
* **deps:** change pipelne to release and bump ref to 15.7.0 ([e8f2821](https://gitlab.devops.telekom.de/dhei/teams/hyperion/dev/src/jumper-sse/commit/e8f282167a178a6366f27f4dc62aad9eb0de452a))
