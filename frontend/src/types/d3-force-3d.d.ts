// Type declarations for d3-force-3d.
// This module extends d3-force with 3D coordinates (z, vz).
// We define the types directly rather than re-exporting from d3-force
// to avoid a dependency on the d3-force package.

declare module 'd3-force-3d' {
  // ---- Simulated node datum ----
  export interface SimulationNodeDatum {
    index?: number
    x: number
    y: number
    z: number
    vx?: number
    vy?: number
    vz?: number
    fx?: number | null
    fy?: number | null
    fz?: number | null
  }

  // ---- Simulation link datum ----
  export interface SimulationLinkDatum<NodeDatum extends SimulationNodeDatum> {
    source: number | string | NodeDatum
    target: number | string | NodeDatum
    index?: number
  }

  // ---- Simulation ----
  export interface Simulation<
    NodeDatum extends SimulationNodeDatum,
    LinkDatum extends SimulationLinkDatum<NodeDatum> = SimulationLinkDatum<NodeDatum>,
  > {
    restart: () => this
    stop: () => this
    tick: (iterations?: number) => this
    nodes: {
      (): NodeDatum[]
      (nodes: NodeDatum[]): this
    }
    alpha: {
      (): number
      (alpha: number): this
    }
    alphaMin: {
      (): number
      (alphaMin: number): this
    }
    alphaDecay: {
      (): number
      (alphaDecay: number): this
    }
    alphaTarget: {
      (): number
      (alphaTarget: number): this
    }
    velocityDecay: {
      (): number
      (decay: number): this
    }
    force: {
      // Typed lookups for the force names this project registers, so callers get
      // the force-specific accessors (distance/strength/radius) without casts.
      (name: 'link'): ForceLink<NodeDatum, LinkDatum> | undefined
      (name: 'charge'): ForceManyBody<NodeDatum> | undefined
      (name: 'collision'): ForceCollide<NodeDatum> | undefined
      (name: 'center'): ForceCenter<NodeDatum> | undefined
      (name: string): Force<NodeDatum, LinkDatum> | undefined
      (name: string, force: Force<NodeDatum, LinkDatum> | null): this
    }
    find: (x: number, y: number, z: number, radius?: number) => NodeDatum | undefined
    on: {
      (typenames: string, callback: () => void): this
      (typenames: string): ((that: Simulation<NodeDatum, LinkDatum>) => void) | undefined
    }
  }

  // ---- Force ----
  export interface Force<
    NodeDatum extends SimulationNodeDatum,
    LinkDatum extends SimulationLinkDatum<NodeDatum> | undefined = undefined,
  > {
    (alpha: number): void
    initialize?: (nodes: NodeDatum[], ...args: unknown[]) => void
  }

  // ---- Specific forces ----
  export interface ForceLink<
    NodeDatum extends SimulationNodeDatum,
    LinkDatum extends SimulationLinkDatum<NodeDatum>,
  > extends Force<NodeDatum, LinkDatum> {
    links: {
      (): LinkDatum[]
      (links: LinkDatum[]): this
    }
    id: {
      (): (node: NodeDatum) => string | number
      (id: (node: NodeDatum) => string | number): this
    }
    distance: {
      (): number | ((link: LinkDatum) => number)
      (distance: number | ((link: LinkDatum) => number)): this
    }
    strength: {
      (): number | ((link: LinkDatum) => number)
      (strength: number | ((link: LinkDatum) => number)): this
    }
    iterations: {
      (): number
      (iterations: number): this
    }
  }

  export interface ForceManyBody<NodeDatum extends SimulationNodeDatum> extends Force<
    NodeDatum,
    undefined
  > {
    strength: {
      (): number | ((node: NodeDatum) => number)
      (strength: number | ((node: NodeDatum) => number)): this
    }
    theta: {
      (): number
      (theta: number): this
    }
    distanceMin: {
      (): number
      (distance: number): this
    }
    distanceMax: {
      (): number
      (distance: number): this
    }
  }

  export interface ForceCenter<NodeDatum extends SimulationNodeDatum> extends Force<
    NodeDatum,
    undefined
  > {
    x: {
      (): number
      (x: number): this
    }
    y: {
      (): number
      (y: number): this
    }
    z: {
      (): number
      (z: number): this
    }
    strength: {
      (): number
      (strength: number): this
    }
  }

  export interface ForceCollide<NodeDatum extends SimulationNodeDatum> extends Force<
    NodeDatum,
    undefined
  > {
    radius: {
      (): number | ((node: NodeDatum) => number)
      (radius: number | ((node: NodeDatum) => number)): this
    }
    strength: {
      (): number
      (strength: number): this
    }
    iterations: {
      (): number
      (iterations: number): this
    }
  }

  // ---- Factory functions ----
  export function forceSimulation<NodeDatum extends SimulationNodeDatum>(): Simulation<
    NodeDatum,
    SimulationLinkDatum<NodeDatum>
  >
  export function forceSimulation<
    NodeDatum extends SimulationNodeDatum,
    LinkDatum extends SimulationLinkDatum<NodeDatum>,
  >(): Simulation<NodeDatum, LinkDatum>

  export function forceLink<
    NodeDatum extends SimulationNodeDatum,
    LinkDatum extends SimulationLinkDatum<NodeDatum>,
  >(): ForceLink<NodeDatum, LinkDatum>

  export function forceManyBody<NodeDatum extends SimulationNodeDatum>(): ForceManyBody<NodeDatum>

  export function forceCenter<NodeDatum extends SimulationNodeDatum>(
    x?: number,
    y?: number,
    z?: number,
  ): ForceCenter<NodeDatum>

  export function forceCollide<NodeDatum extends SimulationNodeDatum>(): ForceCollide<NodeDatum>
}
