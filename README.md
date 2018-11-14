# fonda

An async pipeline approach to functional core - imperative shell.

## Asynchronous pipeline of steps

Fonda sequentially executes an (a)synchronous series of steps, one after the other, augmenting a context map.

## Syntax

```clojure
(execute config steps ctx-seed on-complete on-anomaly on-exception)
```
- **config** A map with:

      - [opt] anomaly?      A function that gets a map and determines if it is an anomaly
      - [opt] exception-tap A function gets called with the runtime-context when there is an exception
      - [opt] anomaly-tap   A function that gets called with te runtime-context when a step returns an anomaly
      - [opt] log-step-fn   A function that defines how each step adds information to the log

- **steps**: Each item on the steps collection must be either a TapStep, or a ResolverStep

      TapStep:
       - tap:  A function that gets the context but doesn't augment it
       - name: The name of the step

      ResolverStep:
       - resolver: A function that gets the context and assocs the result into it on the given path
       - path:     Path where to assoc the result of the resolver
       - name:     The name of the step
       
- **ctx-seed** The context data that gets passed to the steps functions.
               Must be either a map, or nil
               
- **on-complete**  Callback that gets called with the context if all the steps succeeded
- **on-anomaly**   Callback that gets called with an anomaly when any step returns one
- **on-exception** Callback that gets called with an exception when any step triggers one

### Steps

There are two types of steps: **taps** and **resolvers**.

Resolvers get a path and a function. The data returned will be placed on the context on that path.

Taps only get a function, and if they succeed, the result is ignored.

If any step returns an anomaly, or triggers an exception, the execution of the steps stops and the global taps and 
callbacks will be called.

If any step returns an anomaly, the anomaly-tap will be called with the RuntimeContext, and then the on-anomaly callback

If any step triggers an exception, the exception-tap will be called with the RuntimeContext, and then on-exception callback.

#### Resolver steps

A resolvers are maps with the following keys:

- **resolver** A function that gets a context map and returns data. Can be asynchronous.
               If it returns an anomaly, or it triggers an error, the steps execution will be interrupted.
- **path** A vector that determines where in the context will the resolve result be associated.
- **name** A descriptive name for the step

#### Tap steps

Taps are maps with the following keys:

- **tap**   A function that gets the context map. If it succeeds, the result is then ignored.
            It will still block the steps processing if it is asynchronous, and it will interrupt the steps execution
            if it returns an anomaly, or it triggers an exception
- **name**  A descriptive name for the step

### Runtime context

Global taps are called with the runtime context. The runtime context is a map that contains, among other things, 
the context that is passed to each step. It also contains:

- **step-log** A collection of step logs. By default only step names are logged.
               A **log-step-fn** configuration attribute can be passed in order to customize the logging.
 


## Example

```clojure
(fonda/execute
  {:exception-tap (fn [{:keys [error]}] (js/console.log "Exception happened:" error))
   :anomaly-tap   (fn [{:keys [anomaly]}] (js/console.log "An anomaly happened:" anomaly))}
  [{:path [:remote-thing] :resolver (fn [ctx] (ajax/get "http://remote-thing-url"))}
   {:tap (fn [{:keys [remote-thing]}] (js/console.log "remote thing:" ()))}
   {:path [:remote-thing-processed] :resolver (fn [{:keys [remote-thing]} (process-remote-thing remote-thing)])}]

  (fn [{:keys [remote-thing-processed]}] remote-thing-processed)
  (fn [anomaly] anomaly)
  (fn [error] error))

```