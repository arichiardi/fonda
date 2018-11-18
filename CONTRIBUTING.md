<img src="https://www.elasticpath.com/sites/all/themes/bootstrap/images/elastlic-path-logo-RGB.svg" alt="elasticpath logo" title="elasticpath" align="right" width="150"/>

## Submitting a Pull Request (PR)

Before you submit your Pull Request (PR) consider the following guidelines:

* Search [GitHub](https://github.com/elasticpath/fonda/pulls) for
  an open or closed PR. You don't want to duplicate effort.

* Create your patch **including appropriate test cases**.

* When finished, run the pre-compilation tests:

  ```shell
  yarn test
  ```

* Compile:

  ```shell
  yarn shadow-cljs release lib
  ```

* Add your PR changes to [CHANGELOG.md](./CHANGELOG.md), including the relevant
  link to either the issue or the PR itself.

* In GitHub, open a pull request.

* If we suggest changes then:
  * Make the required updates (please).
  * Re-run the test suites to ensure tests are still passing.
  * Rebase your branch if necessary and force push to your GitHub repository (this will update your PR).

Thank you for your contribution!

## Test driven development workflow

In order to start watching the tests use:

```shell
yarn test:watch
```

This will create the JavaScript file and execute tests on file change, see
`:output-to` in `shadow-cljs.edn` for the location of the file.


## REPL driven development workflow

The command that watches, compiles and starts a REPL is along the lines of:

```
yarn repl
```

The configuration of your editor depends on the editor, of course.

Check these two files for more:

 * [Shadow-cljs Editor Integration](https://shadow-cljs.github.io/docs/UsersGuide.html#_editor_integration)
 * [ClojureScript IntelliJ IDEA and shadow-cljs](https://andrearichiardi.com/blog/posts/clojurescript-cursive-shadow-setup.html)


